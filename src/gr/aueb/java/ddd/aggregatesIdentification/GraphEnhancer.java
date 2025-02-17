package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class GraphEnhancer<T> {
    
    private double creationMultiplier = 1.0; // This will be calibrated dynamically

    // Calibrate the creation multiplier based on call graph data.
    public void calibrateCreationMultiplier(ClusteringGraph<T> graph, List<CallGraph> callGraphs) {
        double totalUpdateEvents = 0.0;
        double totalCreationEvents = 0.0;
        int count = 0;
        
        for (T vertex : graph.getVertices()) {
            for (CallGraph cg : callGraphs) {
                List<CallEdge> edges = CallGraphUtil.getOutgoingEdges(vertex, cg);
                if (edges == null) continue;
                for (CallEdge edge : edges) {
                    if (edge.isInUpdateTransaction()) {
                        totalUpdateEvents++;
                    }
                    if (edge.isCreationEvent()) {
                        totalCreationEvents++;
                    }
                }
                count++;
            }
        }
        double avgUpdate = count > 0 ? totalUpdateEvents / count : 1.0;
        double avgCreation = (count > 0 && totalCreationEvents > 0) ? totalCreationEvents / count : 1.0;
        creationMultiplier = avgCreation > 0 ? avgUpdate / avgCreation : 1.0;
    }

    // Compute coupling score for an edge from parent to child based on call graphs.
    public double computeCouplingScore(T parent, T child, List<CallGraph> callGraphs) {
        double totalWeightedEvents = 0.0;
        double weightedEventsToChild = 0.0;
        
        for (CallGraph cg : callGraphs) {
            List<CallEdge> edges = CallGraphUtil.getOutgoingEdges(parent, cg);
            if (edges == null) continue;
            for (CallEdge edge : edges) {
                double eventWeight = 0.0;
                if (edge.isInUpdateTransaction()) {
                    eventWeight += 1.0;
                }
                if (edge.isCreationEvent()) {
                    eventWeight += creationMultiplier;
                }
                totalWeightedEvents += eventWeight;
            	if (edge.getTarget() != null && edge.getTarget().equals(child)) {
                    weightedEventsToChild += eventWeight;
                }
            }
        }
        // ClassObject parentClass = (ClassObject) parent;
        // ClassObject childClass = (ClassObject) child;
        // System.out.println("Parent: " + parentClass.getName() + ", Child: " + childClass.getName());
        return totalWeightedEvents > 0 ? weightedEventsToChild / totalWeightedEvents : 0.0;
    }

    // Compute a dynamic threshold as the average coupling score across all edges in the graph.
    public double computeDynamicThreshold(ClusteringGraph<T> graph, List<CallGraph> callGraphs) {
        List<Double> scores = new ArrayList<Double>();
        for (T parent : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(parent)) {
                double score = computeCouplingScore(parent, edge.getTarget(), callGraphs);
                scores.add(score);
            }
        }
        
//        double sum = 0.0;
//        for (double s : scores) {
//            sum += s;
//        }
//        return scores.isEmpty() ? 0.5 : sum / scores.size();
        
        Collections.sort(scores);
        // Compute index for the 75th percentile
        int index = (int) Math.ceil(0.95 * scores.size()) - 1;
        index = Math.max(0, index); // Ensure non-negative
        
        return scores.get(index);
    }

    // Enhance the graph by adjusting edge weights and possibly promoting REFERENCE to COUPLED.
    public void enhanceGraph(ClusteringGraph<T> graph, List<CallGraph> callGraphs) {
    	// Find Ownerships
    	HashSet<CreationRecord> allCreationRecords = CallGraphUtil.getAllCreationRecords(callGraphs);
    	for (T parent : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(parent)) {
            	for(CreationRecord record : allCreationRecords) {
            		if(record.getCreatedBy() == parent && record.getCreated() ==  edge.getTarget()) {
            			edge.setType(ClusteringGraph.EdgeType.OWNERSHIP);
            		}
        		}
            }
        }

        // calibrateCreationMultiplier(graph, callGraphs);
        double threshold = computeDynamicThreshold(graph, callGraphs);
        System.out.println("Dynamic Coupling Threshold: " + threshold);
        
        for (T parent : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(parent)) {
                double score = computeCouplingScore(parent, edge.getTarget(), callGraphs);
                double factor = (score >= threshold) ? 1.0 : 0.5;
                double newWeight = edge.getWeight() * factor;
                edge.setWeight(newWeight);
                
                // Promote an edge from REFERENCE to COUPLED if coupling is strong.
                if (edge.getType() == ClusteringGraph.EdgeType.REFERENCE && score >= threshold) {
                    edge.setType(ClusteringGraph.EdgeType.COUPLED);
                }
            }
        }
    }
}
