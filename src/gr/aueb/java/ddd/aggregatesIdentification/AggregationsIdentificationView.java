package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import java.util.List;
import java.util.Map;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;

    @Override
    public void createPartControl(Composite parent) {
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
        try {
            Map<String, List<String>> callGraph = new CallGraphBuilder().buildCallGraph();
            displayCallGraph(callGraph);
        } catch (JavaModelException e) {
            e.printStackTrace();
            text.setText("Error: " + e.getMessage());
        }
    }

    private void displayCallGraph(Map<String, List<String>> callGraph) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
            sb.append("Method: ").append(entry.getKey()).append(", Calls: ").append(entry.getValue()).append("\n");
        }
        text.setText(sb.toString());
    }

    @Override
    public void setFocus() {
        text.setFocus();
    }
}
