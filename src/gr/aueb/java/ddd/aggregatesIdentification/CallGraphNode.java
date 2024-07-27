package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.List;

public class CallGraphNode {
    private String methodName;
    private List<CallGraphNode> calledMethods;

    public CallGraphNode(String methodName) {
        this.methodName = methodName;
        this.calledMethods = new ArrayList<CallGraphNode>();
    }

    public String getMethodName() {
        return methodName;
    }

    public List<CallGraphNode> getCalledMethods() {
        return calledMethods;
    }

    public void addCalledMethod(CallGraphNode calledMethod) {
        this.calledMethods.add(calledMethod);
    }
}
