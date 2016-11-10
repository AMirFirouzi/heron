//  Copyright 2016 Twitter. All rights reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License
package org.apache.storm.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A C-style struct definition of a Vertex to be used with
 * the Graph class.
 * <p>
 * The distance field is designed to hold the length of the
 * shortest unweighted path from the source of the traversal
 * <p>
 * The predecessor field refers to the previous field on
 * the shortest path from the source (i.e. the vertex one edge
 * closer to the source).
 *
 */
public class Vertex implements Comparable<Vertex> {
    /**
     * label for Vertex
     */
    public String name;
    /**
     * length of shortest path from source
     */
    public int distance;
    /**
     * previous vertex on path from sourxe
     */

    /**
     * weights of vertex
     */
    private List<String> weights;

    public Vertex predecessor; // previous vertex

    /**
     * a measure of the structural importance of a vertex.
     * The value should initially be set to zero. A higher
     * centrality score should mean a Vertex is more central.
     */
    private double centrality;
    /**
     * Infinite distance indicates that there is no path
     * from the source to this vertex
     */
    public static final int INFINITY = Integer.MAX_VALUE;

    public Vertex(String v)
    {
        name = v;
        distance = INFINITY; // start as infinity away
        predecessor = null;
        centrality = 0.0;
        weights=new ArrayList<String>();
    }

    /**
     * The name of the Vertex is assumed to be unique, so it
     * is used as a HashCode
     *
     * @see Object#hashCode()
     */
    public int hashCode()
    {
        return name.hashCode();
    }

    public void addWeights(String[] weights){
        this.weights.addAll(Arrays.asList(weights));
    }

    public void addWeights(List<String> weights){
        this.weights.addAll(weights);
    }

    public List<String> getWeights(){
        return this.weights;
    }

    public String getWeightsString(){
        String vw="";
        if(!weights.isEmpty())
            vw="(";
        for (Object s :this.weights) {
            vw+=s.toString()+",";
        }
        if (vw!="")
            vw=vw.substring(0,vw.length()-1)+")";
        return vw;
    }

    public String toString()
    {
        return name;
    }
    /**
     * Compare on the basis of distance from source first and
     * then lexicographically
     */
    public int compareTo(Vertex other)
    {
        int diff = distance - other.distance;
        if (diff != 0)
            return diff;
        else
            return name.compareTo(other.name);
    }
}