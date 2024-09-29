package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.List;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;

public class CallGraphNode {
	MethodObject methodObject;
	ClassObject classObject;
    String methodName;
    boolean isEntityMethod;
    boolean isTransactional;
    List<AbstractVariable> definedFields;
    List<CallGraphNode> calledMethods;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.isEntityMethod = false;
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
}
