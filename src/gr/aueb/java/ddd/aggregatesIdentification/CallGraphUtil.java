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
    public static List<CallGraphEdge> getOutgoingEdges(Object entity, CallGraph callGraph) {
        // check if it is entity
    	if (!(entity instanceof ClassObject))
            return Collections.emptyList();
        	
        List<CallGraphEdge> edges = new ArrayList<CallGraphEdge>();
        ClassObject targetEntity = (ClassObject) entity;
        
        //System.out.println("Entity: " + targetEntity.getName() + " for " + callGraph.getRoot().methodName + " -> Edges: ");
        
    	// check if entity is involved in callGraph
        Boolean entityIsUpdated = false;
        Boolean entityIsCreated = false;
        Boolean entityIsRead = false;
    	if(callGraph.getRoot().definedEntitiesObjects.contains(targetEntity)) {
    		//System.out.println("Target entity is updated");
    		entityIsUpdated = true;
        }
        if(callGraph.getRoot().createdEntitiesObjects.contains(targetEntity)) {
    		//System.out.println("Target entity is created");
        	entityIsCreated = true;
        }
        if(!entityIsUpdated && !entityIsCreated) {
        	return Collections.emptyList();
        }
        
        CallGraphNode root = callGraph.getRoot();
        for(ClassObject updatedEntity : root.definedEntitiesObjects) {
        	if(updatedEntity != targetEntity) {
        		CallGraphEdge edge = new CallGraphEdge(entity, updatedEntity, entityIsUpdated, entityIsCreated, entityIsRead);
        		//System.out.println("\t update " + updatedEntity.getName());
                edges.add(edge);
        	}
        }
        for(ClassObject createdEntity : root.createdEntitiesObjects) {
        	if(createdEntity != targetEntity) {
        		CallGraphEdge edge = new CallGraphEdge(entity, createdEntity, entityIsUpdated, entityIsCreated, entityIsRead);
        		//System.out.println("\t create " + createdEntity.getName());
                edges.add(edge);
        	}
        }
//        for(ClassObject readEntity : root.accessedEntitiesObjects) {
//        	if(readEntity != targetEntity) {
//        		CallEdge edge = new CallEdge(entity, readEntity, entityIsUpdated, entityIsCreated, entityIsRead);
//        		//System.out.println("\t create " + createdEntity.getName());
//                edges.add(edge);
//        	}
//        }
        return edges;
    }
    
    public static HashSet<CreationRecord> getAllCreationRecords(List<CallGraph> callGraphs) {
    	HashSet<CreationRecord> allCreationRecords = new HashSet<CreationRecord>();
    	for (CallGraph cg : callGraphs) {
            CallGraphNode root = cg.getRoot();
            allCreationRecords.addAll(root.creationRecords);
        }
		return allCreationRecords;
    	
    }
}
