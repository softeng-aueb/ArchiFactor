package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

public class LouvainClustering<T> {

    // Perform Louvain clustering on the graph
    public List<Set<T>> louvainClustering(ClusteringGraph<T> graph) {
        Map<T, Integer> nodeToCommunity = new HashMap<T, Integer>();
        Map<Integer, Set<T>> communities = new HashMap<Integer, Set<T>>();

        // Step 1: Initialize each node to its own community
        int communityId = 0;
        for (T node : graph.getVertices()) {
            nodeToCommunity.put(node, communityId);
            communities.put(communityId, new HashSet<T>(Collections.singleton(node)));
            communityId++;
        }

        boolean changed;
        do {
            changed = false;

            // Step 2: Iteratively refine communities
            for (T node : graph.getVertices()) {
                int currentCommunity = nodeToCommunity.get(node);
                Map<Integer, Double> neighborCommunityWeights = new HashMap<Integer, Double>();

                // Calculate modularity contribution from neighboring communities
                for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(node)) {
                    T neighbor = edge.getTarget();
                    int neighborCommunity = nodeToCommunity.get(neighbor);

                    // Add weight to the corresponding community
                    if (neighborCommunityWeights.containsKey(neighborCommunity)) {
                        neighborCommunityWeights.put(neighborCommunity, neighborCommunityWeights.get(neighborCommunity) + edge.getWeight());
                    } else {
                        neighborCommunityWeights.put(neighborCommunity, edge.getWeight());
                    }
                }

                // Find the best community for the node
                int bestCommunity = currentCommunity;
                double maxGain = 0;

                for (Map.Entry<Integer, Double> entry : neighborCommunityWeights.entrySet()) {
                    int targetCommunity = entry.getKey();
                    double gain = entry.getValue();

                    if (gain > maxGain) {
                        maxGain = gain;
                        bestCommunity = targetCommunity;
                    }
                }

                // Move the node if a better community is found
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

        // Step 3: Return the final clusters
        return new ArrayList<Set<T>>(communities.values());
    }
}
