package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;
import gr.uom.java.ast.ClassObject;

public class ClusteringGraph<T> {
    private final Map<T, List<Edge<T>>> adjacencyList = new HashMap<T, List<Edge<T>>>();

    // Add a vertex to the graph
    public void addVertex(T vertex) {
        if (!adjacencyList.containsKey(vertex)) {
            adjacencyList.put(vertex, new ArrayList<Edge<T>>());
        }
    }

    // Add an edge with a weight and an EdgeType (undirected graph)
    public void addEdge(T vertex1, T vertex2, double weight, EdgeType type) {
        addVertex(vertex1);
        addVertex(vertex2);
        adjacencyList.get(vertex1).add(new Edge<T>(vertex2, weight, type));
        adjacencyList.get(vertex2).add(new Edge<T>(vertex1, weight, type));
    }

    // Overloaded addEdge without explicit type (defaults to REFERENCE)
    public void addEdge(T vertex1, T vertex2, double weight) {
        addEdge(vertex1, vertex2, weight, EdgeType.REFERENCE);
    }

    // Get neighbors of a vertex
    public List<Edge<T>> getNeighbors(T vertex) {
        return adjacencyList.getOrDefault(vertex, Collections.<Edge<T>>emptyList());
    }

    // Get all vertices in the graph
    public Set<T> getVertices() {
        return adjacencyList.keySet();
    }

    // Check if vertices have an edge
    public boolean hasEdge(T vertex1, T vertex2) {
        if (!adjacencyList.containsKey(vertex1)) {
            return false;
        }
        for (Edge<T> edge : adjacencyList.get(vertex1)) {
            if (edge.getTarget().equals(vertex2)) {
                return true;
            }
        }
        return false;
    }
    
    public void setEdge(T parent, T child, Edge<T> edgeUpdate) {
        if (!adjacencyList.containsKey(parent)) {
            return;
        }
        for (Edge<T> edge : adjacencyList.get(parent)) {
            if (edge.getTarget().equals(child)) {
                edge.type = edgeUpdate.type;
                edge.weight = edgeUpdate.weight;
            }
        }
    }
    

	public Edge<T> getEdge(T nodeA, T nodeB) {
		if (!adjacencyList.containsKey(nodeA)) {
            return null;
        }
        for (Edge<T> edge : adjacencyList.get(nodeA)) {
            if (edge.getTarget().equals(nodeB)) {
                return edge;
            }
        }
        return null;
	}

    // Helper function to print the graph
    public StringBuilder printGraph() {
    	StringBuilder sb = new StringBuilder();
        for (Map.Entry<T, List<Edge<T>>> entry : adjacencyList.entrySet()) {
            T vertex = entry.getKey();
            List<Edge<T>> edges = entry.getValue();
            ClassObject entity = (ClassObject) vertex;
            sb.append("Vertex " + getSimpleName(entity.getName()) + " is connected to:\n");
            for (Edge<T> edge : edges) {
                ClassObject edgeEntity = (ClassObject) edge.getTarget();
                sb.append("\t(" + getSimpleName(edgeEntity.getName()) + 
                                   ", weight: " + edge.getWeight() + 
                                   ", type: " + edge.getType() + ")\n");
            }
        }
        return sb;
    }

    public static String getSimpleName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return fullName;
        }
        int lastDotIndex = fullName.lastIndexOf('.');
        return (lastDotIndex != -1) ? fullName.substring(lastDotIndex + 1) : fullName;
    }

    // Inner class representing an edge
    public static class Edge<T> {
        private final T target;
        private double weight;
        private EdgeType type;

        public Edge(T target, double weight, EdgeType type) {
            this.target = target;
            this.weight = weight;
            this.type = type;
        }

        public T getTarget() { return target; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public EdgeType getType() { return type; }
        public void setType(EdgeType type) { this.type = type; }
    }
    
    public static enum EdgeType {
        COUPLED,   	 // For coupled entities relationships
        REFERENCE,   // For loose references across aggregates
        OWNERSHIP,	 // For relationships where the parent owns the child
        INHERITANCE  // For JPA @Inheritance hierarchy ties
    }

    public static double baselineFor(ClusteringGraph.EdgeType type) {
        switch (type) {
        case INHERITANCE:
        	return 2.0;
        case OWNERSHIP:
        	return 1.0;
        case COUPLED:
            return 1.0;
        case REFERENCE:
            return 0.1;
        default:
            return 1.0;
    }
}

}
