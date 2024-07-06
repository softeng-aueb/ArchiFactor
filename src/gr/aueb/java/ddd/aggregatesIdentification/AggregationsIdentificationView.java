package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.swt.widgets.Text;

import java.util.List;
import java.util.Map;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;

    @Override
    public void createPartControl(Composite parent) {
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
        try {
			displayCallGraph();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    private void displayCallGraph() throws JavaModelException {
        CallGraphBuilder builder = new CallGraphBuilder();
        Map<String, List<String>> callGraph = builder.buildCallGraph();
        StringBuilder callGraphText = new StringBuilder();

        if (callGraph.isEmpty()) {
            callGraphText.append("No endpoints found.");
        } else {
            for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
                callGraphText.append(entry.getKey()).append(":\n");
                for (String methodCall : entry.getValue()) {
                    callGraphText.append("  -> ").append(methodCall).append("\n");
                }
            }
        }

        text.setText(callGraphText.toString());
    }

    @Override
    public void setFocus() {
        text.setFocus();
    }
}
