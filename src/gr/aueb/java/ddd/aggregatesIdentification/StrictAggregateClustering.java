package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

import gr.aueb.java.ddd.aggregatesIdentification.ClusteringGraph.EdgeType;

public class StrictAggregateClustering<T> {

    /**
     * Performs clustering by grouping nodes that are connected
     * via edges of types OWNERSHIP, EMBEDDED, or VALUE.
     *
     * @param graph the clustering graph with typed edges
     * @return a list of clusters (each a set of nodes)
     */
    public List<Set<T>> cluster(ClusteringGraph<T> graph) {
        // Define strong edge types for aggregation.
        Set<EdgeType> strongTypes = new HashSet<EdgeType>();
        strongTypes.add(EdgeType.OWNERSHIP);
        strongTypes.add(EdgeType.EMBEDDED);
        strongTypes.add(EdgeType.VALUE);

        // Union-Find (Disjoint Set) to group connected nodes
        Map<T, T> parent = new HashMap<T, T>();

        // Initialize each node as its own parent (self-cluster)
        for (T vertex : graph.getVertices()) {
            parent.put(vertex, vertex);
        }

        // Merge clusters based on strong edges
        for (T vertex : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(vertex)) {
                if (strongTypes.contains(edge.getType())) {
                    union(parent, vertex, edge.getTarget());
                }
            }
        }

        // Group nodes by their root parent (final clusters)
        Map<T, Set<T>> clusterMap = new HashMap<T, Set<T>>();
        for (T vertex : graph.getVertices()) {
            T root = find(parent, vertex);
            if (!clusterMap.containsKey(root)) {
                clusterMap.put(root, new HashSet<T>());
            }
            clusterMap.get(root).add(vertex);
        }

        return new ArrayList<Set<T>>(clusterMap.values());
    }

    // Union operation for merging two clusters
    private void union(Map<T, T> parent, T node1, T node2) {
        T root1 = find(parent, node1);
        T root2 = find(parent, node2);
        if (!root1.equals(root2)) {
            parent.put(root2, root1); // Merge the two clusters
        }
    }

    // Find operation with path compression
    private T find(Map<T, T> parent, T node) {
        if (!parent.get(node).equals(node)) {
            parent.put(node, find(parent, parent.get(node))); // Path compression
        }
        return parent.get(node);
    }
}
