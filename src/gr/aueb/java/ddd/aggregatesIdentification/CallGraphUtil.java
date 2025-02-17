package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import gr.uom.java.ast.ClassObject;

public class CallGraphUtil {
    
    /**
     * Returns a list of CallEdge objects representing outgoing calls
     * from the given entity (a ClassObject) in the given call graph.
     */
    public static List<CallEdge> getOutgoingEdges(Object entity, CallGraph callGraph) {
        if (!(entity instanceof ClassObject))
            return Collections.emptyList();
        
        List<CallEdge> edges = new ArrayList<CallEdge>();
        ClassObject targetEntity = (ClassObject) entity;
        
        CallGraphNode root = callGraph.getRoot();
        for(ClassObject updatedEntity : root.definedEntitiesObjects) {
        	if(updatedEntity != targetEntity) {
        		CallEdge edge = new CallEdge(entity, updatedEntity, true, false);
                edges.add(edge);
        	}
        }
        for(ClassObject createdEntity : root.createdEntitiesObjects) {
        	if(createdEntity != targetEntity) {
        		CallEdge edge = new CallEdge(entity, createdEntity, true, true);
                edges.add(edge);
        	}
        }
        return edges;
    }
    
    public static HashSet<CreationRecord> getAllCreationRecords(List<CallGraph> callGraphs) {
    	HashSet<CreationRecord> allCreationRecords = new HashSet<CreationRecord>();
    	for (CallGraph cg : callGraphs) {
            CallGraphNode root = cg.getRoot();
            allCreationRecords.addAll(root.getCreationRecords());
        }
		return allCreationRecords;
    	
    }
}
