package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import java.util.List;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;

    @Override
    public void createPartControl(Composite parent) {
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
        try {
            List<CallGraph> callGraphs = new CallGraphBuilder().buildCallGraphs();
            displayCallGraphs(callGraphs);
        } catch (JavaModelException e) {
            e.printStackTrace();
            text.setText("Error: " + e.getMessage());
        }
    }

    private void displayCallGraphs(List<CallGraph> callGraphs) {
        StringBuilder sb = new StringBuilder();
        for (CallGraph callGraph : callGraphs) {
            for (CallGraphNode node : callGraph.getNodes().values()) {
                sb.append("Endpoint: ").append(node.getMethodName()).append("\n");
                sb.append("Calls: ");
                displayNodeCalls(sb, node, "  ");
                sb.append("\n\n");
            }
        }
        text.setText(sb.toString());
    }

    private void displayNodeCalls(StringBuilder sb, CallGraphNode node, String indent) {
        for (CallGraphNode calledMethod : node.getCalledMethods()) {
            sb.append("\n").append(indent).append(calledMethod.getMethodName());
            displayNodeCalls(sb, calledMethod, indent + "  ");
        }
    }

    @Override
    public void setFocus() {
        text.setFocus();
    }
}
