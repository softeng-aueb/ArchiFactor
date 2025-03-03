package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import gr.uom.java.ast.ClassObject;

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
        int totalParentCalls = 0;
        int totalChildCalls = 0;
        double childParentCalls = 0.0;
        
        for (CallGraph cg : callGraphs) {
            List<CallEdge> parentEdges = CallGraphUtil.getOutgoingEdges(parent, cg);
            List<CallEdge> childEdges = CallGraphUtil.getOutgoingEdges(child, cg);
            if (parentEdges == null || childEdges == null) continue;
            totalParentCalls += parentEdges.size();
            totalChildCalls += childEdges.size();
            
            for (CallEdge edge : parentEdges) {
            	if (edge.getTarget() != null && edge.getTarget().equals(child)) {
            		childParentCalls += (edge.isInUpdateTransaction() | edge.isCreationEvent() ) ? 1.0 : 0.0;
                }
            }
        }
        ClassObject parentClass = (ClassObject) parent;
        ClassObject childClass = (ClassObject) child;
        double score  = (totalParentCalls + totalChildCalls > 0) ? (double)(childParentCalls * 2) / (double)(totalParentCalls + totalChildCalls) : 0.0;
        System.out.println("Parent: " + parentClass.getName() + ", total events: " + totalParentCalls 
        		+ "| to Child: " + childClass.getName() + ", total events: " + totalChildCalls + " - " + childParentCalls + " == score: " + score);
        return score;
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
        
        double sum = 0.0;
        for (double s : scores) {
            sum += s;
        }
        return scores.isEmpty() ? 0.5 : sum / scores.size();
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
            			edge.setWeight(ClusteringGraph.baselineFor(ClusteringGraph.EdgeType.OWNERSHIP));
            			graph.setEdge(edge.getTarget(), parent, edge);
            		}
        		}
            }
        }

        // calibrateCreationMultiplier(graph, callGraphs);
        double threshold = computeDynamicThreshold(graph, callGraphs);
        System.out.println("Dynamic Coupling Threshold: " + threshold + "\n" + "CreationMultiplier: " + creationMultiplier);
        
        for (T parent : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(parent)) {
            	if(edge.getType() != ClusteringGraph.EdgeType.REFERENCE) {
            		continue;
            	}
                double score = computeCouplingScore(parent, edge.getTarget(), callGraphs);
//                double factor = (score >= threshold) ? 1.0 : 0.5;
//                double newWeight = edge.getWeight() * factor;
                if(score != 0.0) edge.setWeight(score);
                
                // Promote an edge from REFERENCE to COUPLED if coupling is strong.
                if (edge.getType() == ClusteringGraph.EdgeType.REFERENCE && score >= threshold) {
                	 System.out.println("Upgrading REFERENCE to COUPLED: " + parent.getClass().getName() + " -> " + edge.getTarget().getClass().getName());
                    edge.setType(ClusteringGraph.EdgeType.COUPLED);
                    edge.setWeight(ClusteringGraph.baselineFor(ClusteringGraph.EdgeType.COUPLED));
                }
                graph.setEdge(parent, edge.getTarget(), edge);
            }
        }
    }
}
