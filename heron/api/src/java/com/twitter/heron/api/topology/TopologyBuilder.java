// Copyright 2016 Twitter. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.heron.api.topology;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.twitter.heron.api.Config;
import com.twitter.heron.api.HeronTopology;
import com.twitter.heron.api.bolt.BasicBoltExecutor;
import com.twitter.heron.api.bolt.IBasicBolt;
import com.twitter.heron.api.bolt.IRichBolt;
import com.twitter.heron.api.generated.TopologyAPI;
import com.twitter.heron.api.graph.Graph;
import com.twitter.heron.api.spout.IRichSpout;

/**
 * TopologyBuilder exposes the Java API for specifying a topology for Heron
 * to execute. Topologies are Thrift structures in the end, but since the Thrift API
 * is so verbose, TopologyBuilder greatly eases the process of creating topologies.
 * The template for creating and submitting a topology looks something like:
 * <p>
 * <pre>
 * TopologyBuilder builder = new TopologyBuilder();
 *
 * builder.setSpout("1", new TestWordSpout(true), 5);
 * builder.setSpout("2", new TestWordSpout(true), 3);
 * builder.setBolt("3", new TestWordCounter(), 3)
 *          .fieldsGrouping("1", new Fields("word"))
 *          .fieldsGrouping("2", new Fields("word"));
 * builder.setBolt("4", new TestGlobalCount())
 *          .globalGrouping("1");
 *
 * Map conf = new HashMap();
 * conf.put(Config.TOPOLOGY_WORKERS, 4);
 *
 * HeronSubmitter.submitTopology("mytopology", conf, builder.createTopology());
 * </pre>
 * <p>
 * Running the exact same topology in simulator (in process), and configuring it to log all tuples
 * emitted, looks like the following. Note that it lets the topology run for 10 seconds
 * before shutting down the local cluster.
 * <p>
 * <pre>
 * TopologyBuilder builder = new TopologyBuilder();
 *
 * builder.setSpout("1", new TestWordSpout(true), 5);
 * builder.setSpout("2", new TestWordSpout(true), 3);
 * builder.setBolt("3", new TestWordCounter(), 3)
 *          .fieldsGrouping("1", new Fields("word"))
 *          .fieldsGrouping("2", new Fields("word"));
 * builder.setBolt("4", new TestGlobalCount())
 *          .globalGrouping("1");
 *
 * Map conf = new HashMap();
 * conf.put(Config.TOPOLOGY_WORKERS, 4);
 * conf.put(Config.TOPOLOGY_DEBUG, true);
 *
 * LocalCluster cluster = new LocalCluster();
 * cluster.submitTopology("mytopology", conf, builder.createTopology());
 * Utils.sleep(10000);
 * cluster.shutdown();
 * </pre>
 * <p>
 * <p>The pattern for TopologyBuilder is to map component ids to components using the setSpout
 * and setBolt methods. Those methods return objects that are then used to declare
 * the inputs for that component.</p>
 */
public class TopologyBuilder {
  private Map<String, BoltDeclarer> bolts = new HashMap<String, BoltDeclarer>();
  private Map<String, SpoutDeclarer> spouts = new HashMap<String, SpoutDeclarer>();

  /**
   * TopologyBuilder exposes the Java API for specifying a topology for Heron
   **/
  public HeronTopology createTopology() {
    Graph g = new Graph();
    TopologyAPI.Topology.Builder bldr = TopologyAPI.Topology.newBuilder();
    // First go thru the spouts
    for (Map.Entry<String, SpoutDeclarer> spout : spouts.entrySet()) {
      spout.getValue().dump(bldr);

    }
    // Then go thru the bolts
    for (Map.Entry<String, BoltDeclarer> bolt : bolts.entrySet()) {
      bolt.getValue().dump(bldr);
    }
    int spoutParallelism;
    //Add Spouts
    for (TopologyAPI.SpoutOrBuilder spout
        : bldr.getSpoutsList()) {
      spoutParallelism = Integer.parseInt(getParallelism(spout));
      for (int i = 1; i <= spoutParallelism; i++) {
        g.addVertex(spout.getComp().getName() + "-" + Integer.toString(i));
      }
    }

    int boltParallelism;
    //Add Bolts
    List<TopologyAPI.Bolt> blts = bldr.getBoltsList();
    for (TopologyAPI.BoltOrBuilder bolt
        : blts) {
      boltParallelism = Integer.parseInt(getParallelism(bolt));
      for (int i = 1; i <= boltParallelism; i++) {
        g.addVertex(bolt.getComp().getName() + "-" + Integer.toString(i));
      }
    }

    //Add Edges Between each Component and its sub-tasks(considering number of instances)
    Iterator<TopologyAPI.Bolt> bolt = bldr.getBoltsList().iterator();
    while (bolt.hasNext()) {
      TopologyAPI.Bolt currentBolt = bolt.next();
      List<TopologyAPI.InputStream> inputs = currentBolt.getInputsList();
      for (TopologyAPI.InputStream input
          : inputs) {
        Object component = getComponent(input.getStream().getComponentName(), bldr);
        int sourceParallelism = Integer.parseInt(getParallelism(component));
        int destParallelism = Integer.parseInt(getParallelism(currentBolt));
        for (int i = 1; i <= sourceParallelism; i++) {
          for (int j = 1; j <= destParallelism; j++) {
            g.addEdge(input.getStream().getComponentName()
                + "-" + Integer.toString(i), currentBolt.getComp().getName()
                + "-" + Integer.toString(j));
          }
        }

      }
    }

    return new HeronTopology(bldr);
  }

  private Object getComponent(String componentName, TopologyAPI.Topology.Builder builder) {
    for (TopologyAPI.SpoutOrBuilder spout
        : builder.getSpoutsList()) {
      if (spout.getComp().getName().equals(componentName)) {
        return spout;
      }
    }
    for (TopologyAPI.BoltOrBuilder bolt
        : builder.getBoltsList()) {
      if (bolt.getComp().getName().equals(componentName)) {
        return bolt;
      }
    }
    return null;
  }

  private String getParallelism(Object component) {
    if (component.equals(null)) {
      return "0";
    }
    String className = component.getClass().getSimpleName();
    String parallelism = "";

    if (className.toString().equals("Spout")) {
      TopologyAPI.SpoutOrBuilder spout = (TopologyAPI.SpoutOrBuilder) component;
      for (TopologyAPI.Config.KeyValue config
          : spout.getComp().getConfig().getKvsList()) {
        if (config.getKey() == Config.TOPOLOGY_COMPONENT_PARALLELISM) {
          parallelism = config.getValue();
        }
      }
    } else if (className.toString().equals("Bolt")) {
      TopologyAPI.BoltOrBuilder bolt = (TopologyAPI.BoltOrBuilder) component;
      for (TopologyAPI.Config.KeyValue config
          : bolt.getComp().getConfig().getKvsList()) {
        if (config.getKey() == Config.TOPOLOGY_COMPONENT_PARALLELISM) {
          parallelism = config.getValue();
        }
      }
    }
    return parallelism;
  }

  private String getBoltParallelism(TopologyAPI.BoltOrBuilder bolt) {
    String parallelism = "";
    for (TopologyAPI.Config.KeyValue config
        : bolt.getComp().getConfig().getKvsList()) {
      if (config.getKey() == Config.TOPOLOGY_COMPONENT_PARALLELISM) {
        parallelism = config.getValue();
      }
    }
    return parallelism;
  }

  /**
   * Define a new bolt in this topology with parallelism of just one thread.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this bolt's outputs.
   * @param bolt the bolt
   * @return use the returned object to declare the inputs to this component
   */
  public BoltDeclarer setBolt(String id, IRichBolt bolt) {
    return setBolt(id, bolt, null);
  }

  /**
   * Define a new bolt in this topology with the specified amount of parallelism.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this bolt's outputs.
   * @param bolt the bolt
   * @param parallelismHint the number of tasks that should be assigned to execute this bolt.
   * Each task will run on a thread in a process somewhere around the cluster.
   * @return use the returned object to declare the inputs to this component
   */
  public BoltDeclarer setBolt(String id, IRichBolt bolt, Number parallelismHint) {
    validateComponentName(id);
    BoltDeclarer b = new BoltDeclarer(id, bolt, parallelismHint);
    bolts.put(id, b);
    return b;
  }

  /**
   * Define a new bolt in this topology. This defines a basic bolt, which is a
   * simpler to use but more restricted kind of bolt. Basic bolts are intended
   * for non-aggregation processing and automate the anchoring/acking process to
   * achieve proper reliability in the topology.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this bolt's outputs.
   * @param bolt the basic bolt
   * @return use the returned object to declare the inputs to this component
   */
  public BoltDeclarer setBolt(String id, IBasicBolt bolt) {
    return setBolt(id, bolt, null);
  }

  /**
   * Define a new bolt in this topology. This defines a basic bolt, which is a
   * simpler to use but more restricted kind of bolt. Basic bolts are intended
   * for non-aggregation processing and automate the anchoring/acking process to
   * achieve proper reliability in the topology.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this bolt's outputs.
   * @param bolt the basic bolt
   * @param parallelismHint the number of tasks that should be assigned to execute this bolt.
   * Each task will run on a thread in a process somwehere around the cluster.
   * @return use the returned object to declare the inputs to this component
   */
  public BoltDeclarer setBolt(String id, IBasicBolt bolt, Number parallelismHint) {
    return setBolt(id, new BasicBoltExecutor(bolt), parallelismHint);
  }

  /**
   * Define a new spout in this topology.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this spout's outputs.
   * @param spout the spout
   */
  public SpoutDeclarer setSpout(String id, IRichSpout spout) {
    return setSpout(id, spout, null);
  }

  /**
   * Define a new spout in this topology with the specified parallelism. If the spout declares
   * itself as non-distributed, the parallelismHint will be ignored and only one task
   * will be allocated to this component.
   *
   * @param id the id of this component. This id is referenced by other components that want to
   * consume this spout's outputs.
   * @param parallelismHint the number of tasks that should be assigned to execute this spout.
   * Each task will run on a thread in a process somwehere around the cluster.
   * @param spout the spout
   */
  public SpoutDeclarer setSpout(String id, IRichSpout spout, Number parallelismHint) {
    validateComponentName(id);
    SpoutDeclarer s = new SpoutDeclarer(id, spout, parallelismHint);
    spouts.put(id, s);
    return s;
  }

  private void validateComponentName(String name) {
    if (name.contains(",")) {
      throw new IllegalArgumentException("Component name should not contain comma(,)");
    }
    if (name.contains(":")) {
      throw new IllegalArgumentException("Component name should not contain colon(:)");
    }
    validateUnusedName(name);
  }

  private void validateUnusedName(String name) {
    if (bolts.containsKey(name)) {
      throw new IllegalArgumentException("Bolt has already been declared for name " + name);
    }
    if (spouts.containsKey(name)) {
      throw new IllegalArgumentException("Spout has already been declared for name " + name);
    }
  }
}

