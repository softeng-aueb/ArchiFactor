package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.HashMap;
import java.util.Map;

public class CallGraph {
    private Map<String, CallGraphNode> nodes;

    public CallGraph() {
        this.nodes = new HashMap<String, CallGraphNode>();
    }

    public void addNode(CallGraphNode node) {
        this.nodes.put(node.getMethodName(), node);
    }

    public Map<String, CallGraphNode> getNodes() {
        return nodes;
    }
}
