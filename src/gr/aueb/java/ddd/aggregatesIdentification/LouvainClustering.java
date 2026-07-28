package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

public class LouvainClustering<T> {
    private static final double RESOLUTION = 1.0;

    // Full Louvain: alternate local moving and graph coarsening until no further merging happens.
    public List<Set<T>> louvainClustering(ClusteringGraph<T> graph, Comparator<T> vertexOrder) {
        List<T> originalNodes = new ArrayList<T>(graph.getVertices());
        if (originalNodes.isEmpty()) {
            return new ArrayList<Set<T>>();
        }

        Collections.sort(originalNodes, vertexOrder);

        Map<T, Integer> originalNodeId = new HashMap<T, Integer>();
        Map<Integer, Set<T>> superNodeMembers = new HashMap<Integer, Set<T>>();
        for (int i = 0; i < originalNodes.size(); i++) {
            T node = originalNodes.get(i);
            originalNodeId.put(node, i);
            Set<T> singleton = new HashSet<T>();
            singleton.add(node);
            superNodeMembers.put(i, singleton);
        }

        ClusteringGraph<Integer> workingGraph = liftToIntegerGraph(graph, originalNodeId, originalNodes);

        while (true) {
            List<Set<Integer>> communities = localMovingPhase(workingGraph);
            if (communities.size() == workingGraph.getVertices().size()) {
                break;
            }

            Map<Integer, Integer> oldIdToNewId = new HashMap<Integer, Integer>();
            Map<Integer, Set<T>> nextSuperNodeMembers = new HashMap<Integer, Set<T>>();
            int newId = 0;
            for (Set<Integer> community : communities) {
                Set<T> expandedMembers = new HashSet<T>();
                for (Integer oldId : community) {
                    oldIdToNewId.put(oldId, newId);
                    expandedMembers.addAll(superNodeMembers.get(oldId));
                }
                nextSuperNodeMembers.put(newId, expandedMembers);
                newId++;
            }
            superNodeMembers = nextSuperNodeMembers;
            workingGraph = coarsen(workingGraph, oldIdToNewId);
        }

        return new ArrayList<Set<T>>(superNodeMembers.values());
    }

    private static <X> ClusteringGraph<Integer> liftToIntegerGraph(ClusteringGraph<X> source, Map<X, Integer> nodeIdMap, List<X> orderedNodes) {
        ClusteringGraph<Integer> result = new ClusteringGraph<Integer>();
        for (X node : orderedNodes) {
            result.addVertex(nodeIdMap.get(node));
        }

        // Each undirected edge is stored twice in the source adjacency lists, so emit once per pair
        // to avoid doubling weights when ClusteringGraph.addEdge re-inserts in both directions.
        Set<Long> emittedPairs = new HashSet<Long>();
        for (X node : orderedNodes) {
            int srcId = nodeIdMap.get(node);
            for (ClusteringGraph.Edge<X> edge : source.getNeighbors(node)) {
                int dstId = nodeIdMap.get(edge.getTarget());
                long key = pairKey(srcId, dstId);
                if (emittedPairs.contains(key)) {
                    continue;
                }
                emittedPairs.add(key);
                result.addEdge(srcId, dstId, edge.getWeight(), edge.getType());
            }
        }
        return result;
    }

    // Build the next-level graph: every community becomes one super-node, cross-community edges are
    // summed into a single inter-super-node edge, and within-community edges collapse into self-loops.
    private static ClusteringGraph<Integer> coarsen(ClusteringGraph<Integer> graph, Map<Integer, Integer> oldIdToNewId) {
        ClusteringGraph<Integer> coarsened = new ClusteringGraph<Integer>();
        Set<Integer> newIds = new HashSet<Integer>(oldIdToNewId.values());
        for (Integer id : newIds) {
            coarsened.addVertex(id);
        }

        // Walk every adjacency entry and accumulate by (srcSuper, dstSuper). Cross-community edges
        // contribute once per side, so aggregation[I][J] (I != J) ends up equal to the sum of those
        // edge weights. Within-community edges contribute from both endpoints, so aggregation[I][I]
        // ends up at 2 × (sum of within-community edge weights).
        Map<Integer, Map<Integer, Double>> aggregation = new HashMap<Integer, Map<Integer, Double>>();
        for (Integer node : graph.getVertices()) {
            int srcSuper = oldIdToNewId.get(node);
            Map<Integer, Double> srcMap = aggregation.get(srcSuper);
            if (srcMap == null) {
                srcMap = new HashMap<Integer, Double>();
                aggregation.put(srcSuper, srcMap);
            }
            for (ClusteringGraph.Edge<Integer> edge : graph.getNeighbors(node)) {
                int dstSuper = oldIdToNewId.get(edge.getTarget());
                Double previous = srcMap.get(dstSuper);
                srcMap.put(dstSuper, (previous == null ? 0.0 : previous) + edge.getWeight());
            }
        }

        Set<Long> emittedPairs = new HashSet<Long>();
        for (Map.Entry<Integer, Map<Integer, Double>> srcEntry : aggregation.entrySet()) {
            int src = srcEntry.getKey();
            for (Map.Entry<Integer, Double> dstEntry : srcEntry.getValue().entrySet()) {
                int dst = dstEntry.getKey();
                double aggregatedWeight = dstEntry.getValue();
                if (src == dst) {
                    // Halve the within-community total because each undirected within-edge was
                    // counted from both endpoints during aggregation.
                    double selfLoopWeight = aggregatedWeight / 2.0;
                    if (selfLoopWeight > 0.0) {
                        coarsened.addEdge(src, src, selfLoopWeight, EdgeType.REFERENCE);
                    }
                    continue;
                }

                long key = pairKey(src, dst);
                if (emittedPairs.contains(key)) {
                    continue;
                }

                emittedPairs.add(key);
                coarsened.addEdge(src, dst, aggregatedWeight, EdgeType.REFERENCE);
            }
        }
        return coarsened;
    }

    private static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static <X> List<Set<X>> localMovingPhase(final ClusteringGraph<X> graph) {
        Map<X, Integer> nodeToCommunity = new HashMap<X, Integer>();
        Map<Integer, Set<X>> communities = new HashMap<Integer, Set<X>>();
        Map<Integer, Double> communityTotalWeight = new HashMap<Integer, Double>();
        final Map<X, Double> nodeIncidentWeight = new HashMap<X, Double>();
        List<X> nodes = new ArrayList<X>(graph.getVertices());

        // Pre-compute k_i per node and the graph-wide 2m used by the modularity formula.
        double graphTotalWeight = 0.0;
        for (X node : nodes) {
            double weight = getTotalEdgeWeight(node, graph);
            nodeIncidentWeight.put(node, weight);
            graphTotalWeight += weight;
        }

        if (graphTotalWeight == 0.0) {
            List<Set<X>> singletons = new ArrayList<Set<X>>();
            for (X node : nodes) {
                Set<X> singleton = new HashSet<X>();
                singleton.add(node);
                singletons.add(singleton);
            }
            return singletons;
        }

        // Sort nodes by highest total edge weight (strongest relationships first)
        Collections.sort(nodes, new Comparator<X>() {
            public int compare(X a, X b) {
                return Double.compare(nodeIncidentWeight.get(b), nodeIncidentWeight.get(a));
            }
        });

        int communityId = 0;
        for (X node : nodes) {
            nodeToCommunity.put(node, communityId);
            Set<X> initialSet = new HashSet<X>();
            initialSet.add(node);
            communities.put(communityId, initialSet);
            communityTotalWeight.put(communityId, nodeIncidentWeight.get(node));
            communityId++;
        }

        boolean changed;
        do {
            changed = false;

            for (X node : nodes) {
                int currentCommunity = nodeToCommunity.get(node);
                double nodeWeight = nodeIncidentWeight.get(node);

                // Skip self-loops: they contribute to k_i and via that to Σ_tot, but the
                // A[i][i] term cancels out in the ΔQ derivation, so adding a self-loop to
                // k_i_in for the current community would penalize moves asymmetrically
                // (only the home community, never any candidate, picks up that weight).
                Map<Integer, Double> neighborCommunityWeights = new HashMap<Integer, Double>();
                for (ClusteringGraph.Edge<X> edge : graph.getNeighbors(node)) {
                    if (edge.getTarget().equals(node)) {
                        continue;
                    }

                    int neighborCommunity = nodeToCommunity.get(edge.getTarget());
                    Double previousWeight = neighborCommunityWeights.get(neighborCommunity);
                    neighborCommunityWeights.put(neighborCommunity, (previousWeight == null ? 0.0 : previousWeight) + edge.getWeight());
                }

                if (!neighborCommunityWeights.containsKey(currentCommunity)) {
                    neighborCommunityWeights.put(currentCommunity, 0.0);
                }

                // Baseline: gain of "rejoining" the current community after notionally removing
                // the node, i.e. evaluated with Σ_tot reduced by k_i.
                int bestCommunity = currentCommunity;
                double bestGain = computeModularityGain(
                    neighborCommunityWeights.get(currentCommunity),
                    communityTotalWeight.get(currentCommunity) - nodeWeight,
                    nodeWeight,
                    graphTotalWeight
                );

                for (Map.Entry<Integer, Double> entry : neighborCommunityWeights.entrySet()) {
                    int targetCommunity = entry.getKey();
                    if (targetCommunity == currentCommunity) {
                        continue;
                    }

                    double gain = computeModularityGain(
                        entry.getValue(),
                        communityTotalWeight.get(targetCommunity),
                        nodeWeight,
                        graphTotalWeight
                    );

                    if (gain > bestGain) {
                        bestGain = gain;
                        bestCommunity = targetCommunity;
                    }
                }

                if (bestCommunity == currentCommunity) {
                    continue;
                }

                communities.get(currentCommunity).remove(node);
                communityTotalWeight.put(currentCommunity, communityTotalWeight.get(currentCommunity) - nodeWeight);
                if (communities.get(currentCommunity).isEmpty()) {
                    communities.remove(currentCommunity);
                    communityTotalWeight.remove(currentCommunity);
                }

                communities.get(bestCommunity).add(node);
                communityTotalWeight.put(bestCommunity, communityTotalWeight.get(bestCommunity) + nodeWeight);
                nodeToCommunity.put(node, bestCommunity);
                changed = true;
            }
        } while (changed);

        return new ArrayList<Set<X>>(communities.values());
    }

    private static <X> double getTotalEdgeWeight(X node, ClusteringGraph<X> graph) {
        double totalWeight = 0.0;
        for (ClusteringGraph.Edge<X> edge : graph.getNeighbors(node)) {
            totalWeight += edge.getWeight();
        }
        return totalWeight;
    }

    // Classical Louvain modularity gain of placing a node into a community:
    // ΔQ ∝ k_i_in − γ · Σ_tot · k_i / (2m).
    private static double computeModularityGain(
		double edgeWeightIntoCommunity, 
		double communityTotalWeight,
        double nodeWeight, 
        double graphTotalWeight
    ) {
        return edgeWeightIntoCommunity - RESOLUTION * communityTotalWeight * nodeWeight / graphTotalWeight;
    }
}
