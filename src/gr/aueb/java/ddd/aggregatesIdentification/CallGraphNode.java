package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;

public class CallGraphNode {
	MethodObject methodObject;
	ClassObject classObject;
    String methodName;
    boolean isEntityMethod;
    boolean isReadOnly;
    boolean transactional;
    HashSet<String> accessedEntities;
    HashSet<String> definedEntities;
    HashSet<ClassObject> allEntities;
    HashSet<String> createdEntities;
    HashSet<ClassObject> createdEntitiesObjects;
    HashSet<ClassObject> definedEntitiesObjects;
    HashSet<String> allEntitiesNames;
    List<AbstractVariable> definedFields;
    List<CallGraphNode> calledMethods;
    HashSet<CreationRecord> creationRecords;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.isEntityMethod = false;
        this.isReadOnly = true;
        this.transactional = false;
        this.accessedEntities = new HashSet<String>();
        this.definedEntities = new HashSet<String>();
        this.createdEntities = new HashSet<String>();
        this.createdEntitiesObjects = new HashSet<ClassObject>();
        this.allEntities = new HashSet<ClassObject>();
        this.definedEntitiesObjects = new HashSet<ClassObject>();
        this.allEntitiesNames = new HashSet<String>();
        this.creationRecords = new HashSet<CreationRecord>();
    }

    public void addCalledMethod(CallGraphNode node) {
        this.calledMethods.add(node);
    }
    
    public void setDefinedFields(List<AbstractVariable> list) {
    	this.definedFields = list;
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
 
    public void setEntityMethod(boolean isEntityMethod) {
        this.isEntityMethod = isEntityMethod;
    }
    
    public void setMethodObject(MethodObject methodObj) {
    	this.methodObject = methodObj;
    }
    
    public void setClassObject(ClassObject classObj) {
    	this.classObject = classObj;
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
