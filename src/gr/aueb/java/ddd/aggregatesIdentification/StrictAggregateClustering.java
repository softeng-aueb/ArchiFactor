package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import gr.aueb.java.ddd.aggregatesIdentification.ClusteringGraph.EdgeType;

public class StrictAggregateClustering<T> {

    /**
     * Performs custom clustering by grouping nodes that are connected
     * via edges of types OWNERSHIP, EMBEDDED, or VALUE.
     *
     * @param graph the clustering graph with typed edges
     * @return a list of clusters (each a set of nodes)
     */
    public List<Set<T>> cluster(ClusteringGraph<T> graph) {
        // Define allowed edge types for strong coupling.
        Set<EdgeType> allowedTypes = new HashSet<EdgeType>();
        allowedTypes.add(EdgeType.OWNERSHIP);
        allowedTypes.add(EdgeType.EMBEDDED);
        allowedTypes.add(EdgeType.VALUE);
        
        Set<T> visited = new HashSet<T>();
        List<Set<T>> clusters = new ArrayList<Set<T>>();
        
        // For each vertex, if not visited, perform DFS over allowed edges.
        for (T vertex : graph.getVertices()) {
            if (!visited.contains(vertex)) {
                Set<T> component = new HashSet<T>();
                dfs(vertex, visited, component, graph, allowedTypes);
                clusters.add(component);
            }
        }
        return clusters;
    }
    
    // DFS helper that traverses only edges with allowed types.
    private void dfs(T vertex, Set<T> visited, Set<T> component,
                     ClusteringGraph<T> graph, Set<EdgeType> allowedTypes) {
        visited.add(vertex);
        component.add(vertex);
        for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(vertex)) {
            if (allowedTypes.contains(edge.getType())) {
                T target = edge.getTarget();
                if (!visited.contains(target)) {
                    dfs(target, visited, component, graph, allowedTypes);
                }
            }
        }
    }
}
