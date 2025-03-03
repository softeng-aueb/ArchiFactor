package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

public class LouvainClustering<T> {

    private static final int LOUVAIN_ITERATIONS = 5;

    public List<Set<T>> louvainClustering(ClusteringGraph<T> graph) {
        Map<List<Set<T>>, Integer> clusteringCounts = new HashMap<List<Set<T>>, Integer>();

        for (int i = 0; i < LOUVAIN_ITERATIONS; i++) {
            List<Set<T>> clustering = singleLouvainRun(graph);
            if (clusteringCounts.containsKey(clustering)) {
                clusteringCounts.put(clustering, clusteringCounts.get(clustering) + 1);
            } else {
                clusteringCounts.put(clustering, 1);
            }
        }

        return getMostFrequentClustering(clusteringCounts);
    }

    private List<Set<T>> singleLouvainRun(final ClusteringGraph<T> graph) {
        Map<T, Integer> nodeToCommunity = new HashMap<T, Integer>();
        Map<Integer, Set<T>> communities = new HashMap<Integer, Set<T>>();
        List<T> nodes = new ArrayList<T>(graph.getVertices());

        // Sort nodes by highest total edge weight (strongest relationships first)
        Collections.sort(nodes, new Comparator<T>() {
            public int compare(T a, T b) {
                double weightA = getTotalEdgeWeight(a, graph);
                double weightB = getTotalEdgeWeight(b, graph);
                return Double.compare(weightB, weightA); // Descending order
            }
        });

        int communityId = 0;
        for (T node : nodes) {
            nodeToCommunity.put(node, communityId);
            Set<T> initialSet = new HashSet<T>();
            initialSet.add(node);
            communities.put(communityId, initialSet);
            communityId++;
        }

        boolean changed;
        do {
            changed = false;

            // Sort nodes again before each iteration (ensuring stable processing order)
            Collections.sort(nodes, new Comparator<T>() {
                public int compare(T a, T b) {
                    double weightA = getTotalEdgeWeight(a, graph);
                    double weightB = getTotalEdgeWeight(b, graph);
                    return Double.compare(weightB, weightA);
                }
            });

            for (T node : nodes) {
                int currentCommunity = nodeToCommunity.get(node);
                Map<Integer, Double> neighborCommunityWeights = new HashMap<Integer, Double>();

                for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(node)) {
                    T neighbor = edge.getTarget();
                    int neighborCommunity = nodeToCommunity.get(neighbor);
                    
                    if (!neighborCommunityWeights.containsKey(neighborCommunity)) {
                        neighborCommunityWeights.put(neighborCommunity, 0.0);
                    }
                    neighborCommunityWeights.put(neighborCommunity,
                            neighborCommunityWeights.get(neighborCommunity) + edge.getWeight());
                }

                int bestCommunity = currentCommunity;
                double maxGain = -1;

                for (Map.Entry<Integer, Double> entry : neighborCommunityWeights.entrySet()) {
                    int targetCommunity = entry.getKey();
                    double gain = entry.getValue();

                    if (gain > maxGain) {
                        maxGain = gain;
                        bestCommunity = targetCommunity;
                    }
                }

                if (bestCommunity != currentCommunity) {
                    communities.get(currentCommunity).remove(node);
                    if (communities.get(currentCommunity).isEmpty()) {
                        communities.remove(currentCommunity);
                    }

                    if (!communities.containsKey(bestCommunity)) {
                        communities.put(bestCommunity, new HashSet<T>());
                    }
                    communities.get(bestCommunity).add(node);
                    nodeToCommunity.put(node, bestCommunity);
                    changed = true;
                }
            }
        } while (changed);

        return new ArrayList<Set<T>>(communities.values());
    }

    private double getTotalEdgeWeight(T node, ClusteringGraph<T> graph) {
        double totalWeight = 0.0;
        for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(node)) {
            totalWeight += edge.getWeight();
        }
        return totalWeight;
    }

    private List<Set<T>> getMostFrequentClustering(Map<List<Set<T>>, Integer> clusteringCounts) {
        List<Set<T>> bestClustering = null;
        int maxCount = 0;

        for (Map.Entry<List<Set<T>>, Integer> entry : clusteringCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestClustering = entry.getKey();
            }
        }
        return bestClustering;
    }
}
