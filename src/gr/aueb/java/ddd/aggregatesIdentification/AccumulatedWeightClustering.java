package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.*;

public class AccumulatedWeightClustering<T> {
    private final ClusteringGraph<T> graph;
    private final Map<T, Double> accumulatedWeights = new HashMap<T, Double>();
    private final List<Set<T>> clusters = new ArrayList<Set<T>>();

    // Use 50% of the average accumulated weight as the threshold.
    private final double THRESHOLD_FACTOR = 0.5;
    // Define a minimum edge weight (if needed) for other purposes.
    private final double MIN_EDGE_THRESHOLD = 0.5;
    // Scaling factor for weak (call graph) edges when tie breaking.
    private final double CALL_GRAPH_SCALING_FACTOR = 0.3;
    // A numeric threshold to decide whether an edge is "strong" (association) or weak.
    private final double STRONG_EDGE_THRESHOLD = 0.9;

    public AccumulatedWeightClustering(ClusteringGraph<T> graph) {
        this.graph = graph;
        calculateAccumulatedWeights();
    }

    // Calculate the total accumulated weight for each vertex.
    // (Assumes that the edges already have their intended weights.)
    private void calculateAccumulatedWeights() {
        for (T vertex : graph.getVertices()) {
            double totalWeight = 0.0;
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(vertex)) {
                totalWeight += edge.getWeight();
            }
            accumulatedWeights.put(vertex, totalWeight);
        }
    }

    // Compute the threshold as a fraction of the average accumulated weight.
    private double computeWeightThreshold() {
        double sum = 0.0;
        for (double weight : accumulatedWeights.values()) {
            sum += weight;
        }
        double average = (accumulatedWeights.size() > 0) ? sum / accumulatedWeights.size() : 0.0;
        return THRESHOLD_FACTOR * average;
    }

    // Perform clustering using the modified criteria.
    public List<Set<T>> performClustering() {
        double threshold = computeWeightThreshold();
        Set<T> visited = new HashSet<T>();

        for (T vertex : graph.getVertices()) {
            // Start a new cluster only if the vertex's own accumulated weight is above the threshold.
            if (!visited.contains(vertex) && accumulatedWeights.get(vertex) > threshold) {
                Set<T> cluster = new HashSet<T>();
                buildCluster(vertex, cluster, visited, threshold);
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    // Recursively build clusters.
    // A neighbor is added if:
    //   - The connecting edge is strong (its weight is >= STRONG_EDGE_THRESHOLD), OR
    //   - Otherwise, if the connecting edge is weak (< STRONG_EDGE_THRESHOLD), the neighbor is included only if its own
    //     accumulated weight exceeds the threshold.
    private void buildCluster(T vertex, Set<T> cluster, Set<T> visited, double threshold) {
        visited.add(vertex);
        cluster.add(vertex);

        for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(vertex)) {
            T neighbor = edge.getTarget();
            if (!visited.contains(neighbor)) {
                if (edge.getWeight() >= STRONG_EDGE_THRESHOLD) {
                    // Strong edge: always include neighbor.
                    buildCluster(neighbor, cluster, visited, threshold);
                } else {
                    // Weak edge: include neighbor only if its accumulated weight is above threshold.
                    if (accumulatedWeights.get(neighbor) > threshold) {
                        buildCluster(neighbor, cluster, visited, threshold);
                    }
                }
            }
        }
    }

    // Resolve conflicts when an entity appears in multiple clusters.
    public List<Set<T>> resolveConflicts() {
        Map<T, Set<Set<T>>> entityToClusters = new HashMap<T, Set<Set<T>>>();

        // Collect all clusters an entity appears in.
        for (Set<T> cluster : clusters) {
            for (T entity : cluster) {
                if (!entityToClusters.containsKey(entity)) {
                    entityToClusters.put(entity, new HashSet<Set<T>>());
                }
                entityToClusters.get(entity).add(cluster);
            }
        }

        List<Set<T>> newClusters = new ArrayList<Set<T>>();
        Set<Set<T>> processedClusters = new HashSet<Set<T>>();

        // Resolve conflicts entity-by-entity.
        for (T entity : entityToClusters.keySet()) {
            Set<Set<T>> potentialClusters = entityToClusters.get(entity);
            if (potentialClusters.size() > 1) {
                // Select the best cluster for this entity.
                Set<T> bestCluster = selectBestCluster(entity, potentialClusters);
                if (!processedClusters.contains(bestCluster)) {
                    newClusters.add(new HashSet<T>(bestCluster));
                    processedClusters.add(bestCluster);
                }
                // Remove the entity from other clusters.
                for (Set<T> cluster : potentialClusters) {
                    if (cluster != bestCluster) {
                        cluster.remove(entity);
                    }
                }
            } else {
                Set<T> cluster = potentialClusters.iterator().next();
                if (!processedClusters.contains(cluster)) {
                    newClusters.add(new HashSet<T>(cluster));
                    processedClusters.add(cluster);
                }
            }
        }

        clusters.clear();
        clusters.addAll(newClusters);
        return clusters;
    }

    // Select the best cluster for an entity based on connection weights.
    // For each edge from the entity to a vertex in the cluster, if the edge is strong (>= STRONG_EDGE_THRESHOLD),
    // it counts as an association edge; otherwise, it is scaled as a weak edge.
    private Set<T> selectBestCluster(T entity, Set<Set<T>> potentialClusters) {
        Set<T> bestCluster = null;
        double maxAssociationWeight = Double.NEGATIVE_INFINITY;
        double maxTotalWeight = Double.NEGATIVE_INFINITY;

        for (Set<T> cluster : potentialClusters) {
            double associationWeight = 0.0;
            double totalWeight = 0.0;

            // For every edge from the entity, check if its target is in the cluster.
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(entity)) {
                if (cluster.contains(edge.getTarget())) {
                    double weight = edge.getWeight();
                    double effectiveWeight = (weight >= STRONG_EDGE_THRESHOLD) ? weight : weight * CALL_GRAPH_SCALING_FACTOR;
                    totalWeight += effectiveWeight;
                    if (weight >= STRONG_EDGE_THRESHOLD) {
                        associationWeight += effectiveWeight;
                    }
                }
            }

            if (associationWeight > maxAssociationWeight) {
                maxAssociationWeight = associationWeight;
                maxTotalWeight = totalWeight;
                bestCluster = cluster;
            } else if (associationWeight == maxAssociationWeight && totalWeight > maxTotalWeight) {
                maxTotalWeight = totalWeight;
                bestCluster = cluster;
            }
        }
        return bestCluster;
    }

    // Getter method to retrieve the final clusters.
    public List<Set<T>> getClusters() {
        return clusters;
    }
}
