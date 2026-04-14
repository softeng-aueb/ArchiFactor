package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;

public class CallGraphNode {
    String methodName;
    MethodObject methodObject;
	ClassObject classObject;
    boolean isEntityMethod;
    boolean isTransactional;
    HashSet<String> allEntities;
    HashSet<ClassObject> allEntitiesObjects;
    HashSet<String> createdEntities;
    HashSet<ClassObject> createdEntitiesObjects;
    HashSet<String> definedEntities;
    HashSet<ClassObject> definedEntitiesObjects;
    HashSet<String> accessedEntities;
    HashSet<ClassObject> accessedEntitiesObjects;
    List<CallGraphNode> calledMethods;
    HashSet<CreationRecord> creationRecords;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.isEntityMethod = false;
        this.isTransactional = false;
        this.accessedEntities = new HashSet<String>();
        this.definedEntities = new HashSet<String>();
        this.createdEntities = new HashSet<String>();
        this.createdEntitiesObjects = new HashSet<ClassObject>();
        this.accessedEntitiesObjects = new HashSet<ClassObject>();
        this.allEntitiesObjects = new HashSet<ClassObject>();
        this.definedEntitiesObjects = new HashSet<ClassObject>();
        this.allEntities = new HashSet<String>();
        this.creationRecords = new HashSet<CreationRecord>();
    }

    public void addCalledMethod(CallGraphNode node) {
        this.calledMethods.add(node);
    }

    public String getMethodName() {
        return methodName;
    }

    public List<CallGraphNode> getCalledMethods() {
        return calledMethods;
    }
    
    public boolean isEntityMethod() {
        return isEntityMethod;
    }

    public boolean isReadOnly() {
        return definedEntitiesObjects.isEmpty() && createdEntitiesObjects.isEmpty();
    }
 
    public void setEntityMethod(boolean isEntityMethod) {
        this.isEntityMethod = isEntityMethod;
    }
    
    public void setMethodObject(MethodObject methodObj) {
    	this.methodObject = methodObj;
    }
    
    public void setClassObject(ClassObject classObj) {
    	this.classObject = classObj;
    }

    public void setTransactional(boolean transactional) {
    	this.isTransactional = transactional;
    }
    
    public HashSet<ClassObject> getDefinedEntitiesObjects() {
    	return this.definedEntitiesObjects;
    }
    
    public void addCreationRecord(CreationRecord record) {
        this.creationRecords.add(record);
    }
    
    public HashSet<CreationRecord> getCreationRecords() {
        return this.creationRecords;
    }
}
