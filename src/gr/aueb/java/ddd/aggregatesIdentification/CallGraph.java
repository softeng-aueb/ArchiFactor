package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.HashMap;
import java.util.Map;

public class CallGraph {
    private CallGraphNode root;
    private Map<String, CallGraphNode> nodes;

    public CallGraph() {
        this.nodes = new HashMap<String, CallGraphNode>();
    }

    public void addNode(CallGraphNode node) {
        if (this.root == null) {
            this.root = node;
        }
        nodes.put(node.getMethodName(), node);
    }

    public CallGraphNode getRoot() {
        return this.root;
    }

    public Map<String, CallGraphNode> getNodes() {
        return nodes;
    }
}
