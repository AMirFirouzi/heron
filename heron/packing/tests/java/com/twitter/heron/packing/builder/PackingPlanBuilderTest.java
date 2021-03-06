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
package com.twitter.heron.packing.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.twitter.heron.common.basics.Pair;
import com.twitter.heron.packing.AssertPacking;
import com.twitter.heron.packing.PackingTestHelper;
import com.twitter.heron.packing.ResourceExceededException;
import com.twitter.heron.spi.common.Constants;
import com.twitter.heron.spi.packing.InstanceId;
import com.twitter.heron.spi.packing.PackingException;
import com.twitter.heron.spi.packing.PackingPlan;
import com.twitter.heron.spi.packing.Resource;

import static org.junit.Assert.assertEquals;

public class PackingPlanBuilderTest {

  private static final String TOPOLOGY_ID = "testTopologyId";

  private List<Container> testContainers;

  @Before
  public void init() {
    testContainers = new ArrayList<>();
    testContainers.add(new Container(3, null, 5));
    testContainers.add(new Container(6, null, 20));
    testContainers.add(new Container(4, null, 20));
  }

  @Test
  public void testScorerSortById() {
    doScorerSortTest(new ContainerIdScorer(), new int[] {0, 2, 1});
  }

  @Test
  public void testScorerSortBySpecificId() {
    doScorerSortTest(new ContainerIdScorer(4, 6), new int[] {2, 1, 0});
  }

  @Test
  public void testScorerSortByPadding() {
    doScorerSortTest(new TestPaddingScorer(true), new int[] {0, 1, 2});
  }

  @Test
  public void testScorerSortByPaddingReverse() {
    doScorerSortTest(new TestPaddingScorer(false), new int[] {1, 2, 0});
  }

  @Test
  public void testMultiScorerSort() {
    List<Scorer<Container>> scorers = new ArrayList<>();
    scorers.add(new TestPaddingScorer(true));
    scorers.add(new ContainerIdScorer());
    doScorerSortTest(scorers, new int[] {0, 2, 1});
  }

  @Test
  public void testMultiScorerSortReverse() {
    List<Scorer<Container>> scorers = new ArrayList<>();
    scorers.add(new TestPaddingScorer(false));
    scorers.add(new ContainerIdScorer());
    doScorerSortTest(scorers, new int[] {2, 1, 0});
  }

  private void doScorerSortTest(Scorer<Container> scorer, int[] expectedOrder) {
    List<Scorer<Container>> scorers = new ArrayList<>();
    scorers.add(scorer);
    doScorerSortTest(scorers, expectedOrder);
  }

  private void doScorerSortTest(List<Scorer<Container>> scorers, int[] expectedOrder) {
    List<Container> sorted = PackingPlanBuilder.sortContainers(scorers, testContainers);

    assertEquals(sorted.size(), testContainers.size());
    assertEquals(expectedOrder.length, testContainers.size());

    int i = 0;
    for (int expectedIndex : expectedOrder) {
      assertEquals(String.format(
          "Expected item %s in the sorted collection to be item %s from the original collection",
          i, expectedIndex), testContainers.get(expectedIndex), sorted.get(i++));
    }
  }

  /**
   * Tests the getContainers method.
   */
  @Test
  public void testGetContainers() throws ResourceExceededException {

    int paddingPercentage = 10;
    Map<Integer, List<InstanceId>> packing = new HashMap<>();
    packing.put(7, Arrays.asList(
        new InstanceId("spout", 1, 0),
        new InstanceId("bolt", 2, 0)));
    packing.put(3, Arrays.asList(
        new InstanceId("spout", 3, 0),
        new InstanceId("bolt", 4, 0)));

    PackingPlan packingPlan = generatePacking(packing);
    Map<Integer, Container> containers =
        PackingPlanBuilder.getContainers(packingPlan, paddingPercentage);
    assertEquals(packing.size(), containers.size());
    for (Integer containerId : packing.keySet()) {
      Container foundContainer = containers.get(containerId);
      assertEquals(paddingPercentage, foundContainer.getPaddingPercentage());
      assertEquals(packingPlan.getMaxContainerResources(), foundContainer.getCapacity());
      assertEquals(2, foundContainer.getInstances().size());
    }
  }

  private static PackingPlan generatePacking(Map<Integer, List<InstanceId>> basePacking)
      throws RuntimeException {
    Resource resource = new Resource(2.0, 6 * Constants.GB, 25 * Constants.GB);

    Set<PackingPlan.ContainerPlan> containerPlans = new HashSet<>();

    for (int containerId : basePacking.keySet()) {
      List<InstanceId> instanceList = basePacking.get(containerId);

      Set<PackingPlan.InstancePlan> instancePlans = new HashSet<>();

      for (InstanceId instanceId : instanceList) {
        String componentName = instanceId.getComponentName();
        Resource instanceResource;
        switch (componentName) {
          case "bolt":
            instanceResource = new Resource(1.0, 2 * Constants.GB, 10 * Constants.GB);
            break;
          case "spout":
            instanceResource = new Resource(1.0, 3 * Constants.GB, 10 * Constants.GB);
            break;
          default:
            throw new RuntimeException(String.format("%s is not a valid component name",
                componentName));
        }
        instancePlans.add(new PackingPlan.InstancePlan(instanceId, instanceResource));
      }
      PackingPlan.ContainerPlan containerPlan =
          new PackingPlan.ContainerPlan(containerId, instancePlans, resource);

      containerPlans.add(containerPlan);
    }

    return new PackingPlan("", containerPlans);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private final Pair<Integer, InstanceId>[] testComponentInstances = new Pair[] {
      new Pair<>(1, new InstanceId("componentA", 0, 0)),
      new Pair<>(3, new InstanceId("componentA", 1, 1)),
      new Pair<>(3, new InstanceId("componentB", 2, 0))
  };

  @Test
  public void testBuildPackingPlan() throws ResourceExceededException {
    doCreatePackingPlanTest(testComponentInstances);
  }

  @Test
  public void testAddToPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, InstanceId>[] added = new Pair[] {
        new Pair<>(1, new InstanceId("componentB", 3, 1)),
        new Pair<>(4, new InstanceId("componentA", 4, 2))
    };
    PackingPlan updatedPlan =
        PackingTestHelper.addToTestPackingPlan(TOPOLOGY_ID, plan, added, 0);

    Pair<Integer, InstanceId>[] expected = concat(testComponentInstances, added);
    AssertPacking.assertPackingPlan(TOPOLOGY_ID, expected, updatedPlan);
  }

  @Test(expected = ResourceExceededException.class)
  public void testExceededCapacityAddingToPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, InstanceId>[] added = new Pair[] {
        new Pair<>(3, new InstanceId("componentB", 3, 1))
    };
    PackingTestHelper.addToTestPackingPlan(TOPOLOGY_ID, plan, added, 0);
  }

  @Test(expected = PackingException.class)
  public void testDuplicateTaskIdAddingToPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, InstanceId>[] added = new Pair[] {
        new Pair<>(1, new InstanceId("componentB", 2, 1))
    };
    PackingTestHelper.addToTestPackingPlan(TOPOLOGY_ID, plan, added, 0);
  }

  // TODO: enable this after fixing https://github.com/twitter/heron/issues/1579
  //@Test(expected = PackingException.class)
  public void testDuplicateComponentIndex() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, InstanceId>[] added = new Pair[] {
        new Pair<>(1, new InstanceId("componentA", 3, 0))
    };
    PackingTestHelper.addToTestPackingPlan(TOPOLOGY_ID, plan, added, 0);
  }

  @Test
  public void testRemoveFromPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, String>[] removed = new Pair[] {
        new Pair<>(1, "componentA"),
        new Pair<>(3, "componentA")
    };
    PackingPlan updatedPlan =
        PackingTestHelper.removeFromTestPackingPlan(TOPOLOGY_ID, plan, removed, 0);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, InstanceId>[] expected = new Pair[] {
        new Pair<>(3, new InstanceId("componentB", 2, 0))
    };
    AssertPacking.assertPackingPlan(TOPOLOGY_ID, expected, updatedPlan);
  }

  @Test(expected = PackingException.class)
  public void testInvalidContainerRemoveFromPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, String>[] removed = new Pair[] {
        new Pair<>(7, "componentA")
    };
    PackingTestHelper.removeFromTestPackingPlan(TOPOLOGY_ID, plan, removed, 0);
  }

  @Test(expected = PackingException.class)
  public void testInvalidComponentRemoveFromPackingPlan() throws ResourceExceededException {
    PackingPlan plan = doCreatePackingPlanTest(testComponentInstances);

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pair<Integer, String>[] removed = new Pair[] {
        new Pair<>(1, "componentC")
    };
    PackingTestHelper.removeFromTestPackingPlan(TOPOLOGY_ID, plan, removed, 0);
  }

  private static PackingPlan doCreatePackingPlanTest(
      Pair<Integer, InstanceId>[] instances) throws ResourceExceededException {
    PackingPlan plan =
        PackingTestHelper.createTestPackingPlan(TOPOLOGY_ID, instances, 0);
    AssertPacking.assertPackingPlan(TOPOLOGY_ID, instances, plan);
    return plan;
  }

  private static <T> T[] concat(T[] first, T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static class TestPaddingScorer implements Scorer<Container> {
    private boolean ascending;

    TestPaddingScorer(boolean ascending) {
      this.ascending = ascending;
    }

    @Override
    public boolean sortAscending() {
      return ascending;
    }

    @Override
    public double getScore(Container container) {
      return container.getPaddingPercentage();
    }
  }
}
