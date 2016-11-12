/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.storm.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by amir on 11/9/16.
 */
public class Edge {

  private String name;

  private String src;

  private String dest;

  private List<String> weights;

  public Edge(String src, String dest) {
    this.src = src;
    this.dest = dest;
    this.name = src + ">" + dest;
    weights = new ArrayList<String>();
  }

  public Edge(String src, String dest, String[] vertexWeights) {
    this.src = src;
    this.dest = dest;
    this.name = src + " > " + dest;
    addWeights(vertexWeights);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSrc() {
    return src;
  }

  public void setSrc(String src) {
    this.src = src;
  }

  public String getDest() {
    return dest;
  }

  public void setDest(String dest) {
    this.dest = dest;
  }

  public List<String> getWeights() {
    return weights;
  }

  public String getWeightsString() {
    String ew = "";
    if (!weights.isEmpty()) {
      ew = "-(";
    }
    for (Object s : this.weights) {
      ew += s.toString() + ",";
    }
    if (!ew.isEmpty()) {
      ew = ew.substring(0, ew.length() - 1) + ")";
    }
    return ew;
  }

  public void addWeights(List<String> vertexWeights) {
    this.weights = vertexWeights;
  }

  public void addWeights(String[] vertexWeights) {
    this.weights = Arrays.asList(vertexWeights);
  }
}
