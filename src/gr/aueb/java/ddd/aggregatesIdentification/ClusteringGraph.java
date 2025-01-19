package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

public class ClusteringGraph<T> {
    private final Map<T, List<Edge<T>>> adjacencyList = new HashMap<T, List<Edge<T>>>();

    // Add a vertex to the graph
    public void addVertex(T vertex) {
        adjacencyList.putIfAbsent(vertex, new ArrayList<Edge<T>>());
    }

    // Add an edge with a weight (undirected graph)
    public void addEdge(T vertex1, T vertex2, double weight) {
        adjacencyList.putIfAbsent(vertex1, new ArrayList<Edge<T>>());
        adjacencyList.putIfAbsent(vertex2, new ArrayList<Edge<T>>());
        adjacencyList.get(vertex1).add(new Edge<T>(vertex2, weight));
        adjacencyList.get(vertex2).add(new Edge<T>(vertex1, weight));
    }

    // Get neighbors of a vertex
    public List<Edge<T>> getNeighbors(T vertex) {
        return adjacencyList.getOrDefault(vertex, Collections.<Edge<T>>emptyList());
    }

    // Get all vertices in the graph
    public Set<T> getVertices() {
        return adjacencyList.keySet();
    }

    // Perform clustering by finding connected components
    public List<Set<T>> findConnectedComponents() {
        Set<T> visited = new HashSet<T>();
        List<Set<T>> components = new ArrayList<Set<T>>();

        for (T vertex : adjacencyList.keySet()) {
            if (!visited.contains(vertex)) {
                Set<T> component = new HashSet<T>();
                dfs(vertex, visited, component);
                components.add(component);
            }
        }
        return components;
    }

    // Depth-First Search (DFS) helper function
    private void dfs(T vertex, Set<T> visited, Set<T> component) {
        visited.add(vertex);
        component.add(vertex);
        for (Edge<T> edge : getNeighbors(vertex)) {
            if (!visited.contains(edge.getTarget())) {
                dfs(edge.getTarget(), visited, component);
            }
        }
    }

    // Edge class to represent connections
    public static class Edge<T> {
        private final T target;
        private final double weight;

        public Edge(T target, double weight) {
            this.target = target;
            this.weight = weight;
        }

        public T getTarget() {
            return target;
        }

        public double getWeight() {
            return weight;
        }
    }
}

