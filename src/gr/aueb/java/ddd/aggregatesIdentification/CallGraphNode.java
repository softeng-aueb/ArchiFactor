package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import gr.uom.java.ast.ClassObject;

public class CallGraphNode {
    String methodName;
	ClassObject classObject;
    boolean isEntityMethod;
    boolean isTransactional;
    List<CallGraphNode> calledMethods;
    HashSet<String> allEntities;
    HashSet<ClassObject> allEntitiesObjects;
    HashSet<String> createdEntities;
    HashSet<ClassObject> createdEntitiesObjects;
    HashSet<String> definedEntities;
    HashSet<ClassObject> definedEntitiesObjects;
    HashSet<String> accessedEntities;
    HashSet<ClassObject> accessedEntitiesObjects;
    HashSet<CreationRecord> creationRecords;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.isEntityMethod = false;
        this.isTransactional = false;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.allEntities = new HashSet<String>();
        this.allEntitiesObjects = new HashSet<ClassObject>();
        this.createdEntities = new HashSet<String>();
        this.createdEntitiesObjects = new HashSet<ClassObject>();
        this.definedEntities = new HashSet<String>();
        this.definedEntitiesObjects = new HashSet<ClassObject>();
        this.accessedEntities = new HashSet<String>();
        this.accessedEntitiesObjects = new HashSet<ClassObject>();
        this.creationRecords = new HashSet<CreationRecord>();
    }

    public boolean isReadOnly() {
        return definedEntitiesObjects.isEmpty() && createdEntitiesObjects.isEmpty();
    }
}
