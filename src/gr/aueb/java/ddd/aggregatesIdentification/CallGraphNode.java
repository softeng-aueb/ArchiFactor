package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.List;

public class CallGraphNode {
    String methodName;
    private boolean isEntityMethod;
    List<CallGraphNode> calledMethods;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.calledMethods = new ArrayList<CallGraphNode>();
        this.isEntityMethod = false;
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

    public void setEntityMethod(boolean isEntityMethod) {
        this.isEntityMethod = isEntityMethod;
    }
}
