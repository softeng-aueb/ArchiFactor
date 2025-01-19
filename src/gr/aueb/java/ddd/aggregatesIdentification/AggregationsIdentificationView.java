package gr.aueb.java.ddd.aggregatesIdentification;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.association.Association;
import gr.uom.java.ast.association.AssociationDetection;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.HashSet;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.HashMap;
import java.util.List;
//import java.util.Map;
//import java.util.Set;
import java.util.Set;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;
    private ComboViewer projectComboViewer;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(2, false));

        projectComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
        projectComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());

        List<String> projectNames = new ArrayList<String>();
        IJavaProject[] javaProjects = null;
		try {
			javaProjects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        for (IJavaProject javaProject : javaProjects) {
            projectNames.add(javaProject.getElementName());
        }

        projectComboViewer.setInput(projectNames);
        projectComboViewer.getCombo().select(0);

        Button runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Run Aggregation Identification");
        runButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        runButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				runAggregationIdentification();
			}
		});

        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
        GridData textLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        textLayoutData.horizontalSpan = 2;
        text.setLayoutData(textLayoutData);
    }

    private void runAggregationIdentification() {
        String selectedProject = projectComboViewer.getCombo().getText();
        try {
        	
        	// Detect project
            IJavaProject javaProject = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProject(selectedProject);
			SystemObject sysObj = SystemObjectProvider.getSystemObject(javaProject);
            
			// Create call graphs
			List<CallGraph> callGraphs = new CallGraphBuilder(javaProject, sysObj).buildCallGraphs();
			
			// Initialize clustering graph
			ClusteringGraph<ClassObject> clusteringGraph = new ClusteringGraph<ClassObject>();
			for (CallGraph callGraph : callGraphs) {
	            for(ClassObject entity : callGraph.getRoot().allEntities) {
	            	clusteringGraph.addVertex(entity);
	            }
	        }
			
			// Create associations mapping
			AssociationDetection associationsMapper = new AssociationDetection(sysObj);
			System.out.print(associationsMapper);
			// Add edges to associations
			// Set<ClassObject> vertices = clusteringGraph.getVertices();
			Set<ClassObject> vertices = new HashSet<ClassObject>();
			vertices.addAll(clusteringGraph.getVertices());
			for (ClassObject vertex : vertices) {
				List<Association> associations = associationsMapper.getAssociationsOfClass(vertex);
				for (Association association : associations) {
					ClassObject toVertex = sysObj.getClassObject(association.getTo());
					clusteringGraph.addEdge(vertex,  toVertex, 1.0);
				}
			}

        	LouvainClustering<ClassObject> louvain = new LouvainClustering<ClassObject>();
        	List<Set<ClassObject>> clusters = louvain.louvainClustering(clusteringGraph);

        	for (Set<ClassObject> cluster : clusters) {
        	    System.out.println("Cluster: " + cluster);
        	}
			// End of Testing
        	 
            displayCallGraphs(callGraphs);
        } catch (Exception e) {
            e.printStackTrace();
            text.setText("Error: " + e.getMessage());
        }
    }

    private void displayCallGraphs(List<CallGraph> callGraphs) {
        StringBuilder sb = new StringBuilder();
        for (CallGraph callGraph : callGraphs) {
            sb.append("Endpoint: ").append(callGraph.getRoot().getMethodName());
            if(callGraph.getRoot().isReadOnly) {
            	sb.append(" [ReadOnly]");
            }
            if(callGraph.getRoot().transactional) {
            	sb.append(" [Transactional]");
            }
            sb.append("\n");
            sb.append("Accessed Entities: ");
            sb.append(callGraph.getRoot().accessedEntities.toString()).append("\n");
            sb.append("Defined Entities: ");
            sb.append(callGraph.getRoot().definedEntities.toString()).append("\n");
            sb.append("Calls: \n");
            appendCalls(sb, callGraph.getRoot(), "  ");
            sb.append("\n");
        }
        text.setText(sb.toString());
    }

    private void appendCalls(StringBuilder sb, CallGraphNode node, String indent) {
        for (CallGraphNode calledMethod : node.getCalledMethods()) {
            sb.append(indent).append(calledMethod.getMethodName());
            if(calledMethod.isEntityMethod()) {
            	sb.append(" [Entity method]");
            	if(calledMethod.isReadOnly) {
            		sb.append(" [ReadOnly]");
            	}
            	if (calledMethod.definedFields != null && calledMethod.definedFields.size() != 0) {
                	sb.append(" [Changes: ");
                	for (int i = 0; i < calledMethod.definedFields.size(); i++) {
                		sb.append(calledMethod.definedFields.get(i).getVariableName());
                		if (i != calledMethod.definedFields.size() - 1)	sb.append(", ");
                	}
                	sb.append("]");
            	}
            }
            sb.append("\n");
            appendCalls(sb, calledMethod, indent + "  ");
        }
    }    
    
    @Override
    public void setFocus() {
        text.setFocus();
    }
}
