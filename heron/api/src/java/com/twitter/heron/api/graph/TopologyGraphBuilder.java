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

package com.twitter.heron.api.graph;

import java.util.Iterator;

import com.twitter.heron.api.Config;
import com.twitter.heron.api.generated.TopologyAPI;

/**
 * Created by amir on 11/21/16.
 */
public final class TopologyGraphBuilder {

  private TopologyGraphBuilder() {
    //not called
  }

  public static void buildGraph(TopologyAPI.Topology.Builder builder) {
    Graph g = new Graph();
    //Add Vertices for Spouts
    int spoutParallelism;
    for (TopologyAPI.SpoutOrBuilder spout
        : builder.getSpoutsList()) {
      spoutParallelism = Integer.parseInt(getParallelism(spout));
      for (int i = 1; i <= spoutParallelism; i++) {
        g.addVertex(spout.getComp().getName() + "-" + Integer.toString(i));
      }
    }

    int boltParallelism;
    //Add Vertices for Bolts, then Add Edges Between Components(spouts->bolts and bolts->bolts)
    //and their sub-tasks(considering number of instances)
    Iterator<TopologyAPI.Bolt> bolt =
        builder.getBoltsList().iterator();
    while (bolt.hasNext()) {
      TopologyAPI.Bolt currentBolt = bolt.next();
      boltParallelism = Integer.parseInt(getParallelism(currentBolt));
      for (int i = 1; i <= boltParallelism; i++) {
        g.addVertex(currentBolt.getComp().getName() + "-" + Integer.toString(i));
        for (TopologyAPI.InputStream input
            : currentBolt.getInputsList()) {
          Object component = getComponent(input.getStream().getComponentName(),
              builder);
          int sourceParallelism = Integer.parseInt(getParallelism(component));
          for (int j = 1; j <= sourceParallelism; j++) {
            g.addEdge(input.getStream().getComponentName()
                + "-" + Integer.toString(j), currentBolt.getComp().getName()
                + "-" + Integer.toString(i));
          }
        }
      }
    }
    g.writeMetis("metis", false, false, false, 0);
  }

  private static Object getComponent(String componentName, TopologyAPI.Topology.Builder builder) {
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

  private static String getParallelism(Object component) {
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
}
