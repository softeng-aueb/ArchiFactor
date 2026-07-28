package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import gr.uom.java.ast.ClassObject;

public class CallGraphNode {
    String methodName;
	ClassObject classObject;
    boolean isTransactional;
    List<CallGraphNode> calledMethods;
    HashSet<String> createdEntities;
    HashSet<ClassObject> createdEntitiesObjects;
    HashSet<String> definedEntities;
    HashSet<ClassObject> definedEntitiesObjects;
    HashSet<CreationRecord> creationRecords;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.isTransactional = false;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.createdEntities = new HashSet<String>();
        this.createdEntitiesObjects = new HashSet<ClassObject>();
        this.definedEntities = new HashSet<String>();
        this.definedEntitiesObjects = new HashSet<ClassObject>();
        this.creationRecords = new HashSet<CreationRecord>();
    }

    public boolean isReadOnly() {
        return definedEntitiesObjects.isEmpty() && createdEntitiesObjects.isEmpty();
    }

}
