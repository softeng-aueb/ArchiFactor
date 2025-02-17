package gr.aueb.java.ddd.aggregatesIdentification;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.association.Association;
import gr.uom.java.ast.association.AssociationDetection;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;
    private ComboViewer projectComboViewer;
    private Boolean strictAggregates;
    private Boolean displayLogs;
    @SuppressWarnings("unused")
    private int createWeight;
    @SuppressWarnings("unused")
	private int writeWeight;
    @SuppressWarnings("unused")
    private int readWeight;
    

    @Override
    public void createPartControl(Composite parent) {
        // 1) Setup layout with spacing/margins
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;   // Space between rows
        layout.horizontalSpacing = 10; // Space between columns
        layout.marginWidth = 10;       // Left/right margin
        layout.marginHeight = 10;      // Top/bottom margin
        parent.setLayout(layout);

        // Row 1: "Choose project" label + Combo
        Label projectLabel = new Label(parent, SWT.NONE);
        projectLabel.setText("Choose project:");

        projectComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
        projectComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());

        // Populate combo
        List<String> projectNames = new ArrayList<String>();
        try {
            IJavaProject[] javaProjects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
            for (IJavaProject jp : javaProjects) {
                projectNames.add(jp.getElementName());
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        projectComboViewer.setInput(projectNames);
        if (!projectNames.isEmpty()) {
            projectComboViewer.getCombo().select(0);
        }

        // Row 2: "Strict Aggregates" checkbox
        final Button strictCheck = new Button(parent, SWT.CHECK);
        strictCheck.setText("Use Strict Aggregates");
        strictCheck.setSelection(false);
        // Make the checkbox span 2 columns if you want it on its own row:
        GridData strictCheckGD = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        strictCheck.setLayoutData(strictCheckGD);

        // Row 3: "Display Logs" checkbox
        final Button logsCheck = new Button(parent, SWT.CHECK);
        logsCheck.setText("Display Logs");
        logsCheck.setSelection(false);
        // Also span 2 columns if desired:
        GridData logsCheckGD = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        logsCheck.setLayoutData(logsCheckGD);

        // Row 4: Centered "Run" button spanning 2 columns
        Button runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Run Aggregation Identification");
        GridData buttonGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        runButton.setLayoutData(buttonGridData);

        // Row 5: Text area (spanning 2 columns)
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
        GridData textLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        text.setLayoutData(textLayoutData);

        // 2) Assign checkbox selections to class fields on click
        runButton.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
            	strictAggregates = strictCheck.getSelection();
                displayLogs = logsCheck.getSelection();
                runAggregationIdentification();
            }
        });
    }

    private void runAggregationIdentification() {
        String selectedProject = projectComboViewer.getCombo().getText();
        try {
        	
        	// Detect project
            IJavaProject javaProject = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProject(selectedProject);
			SystemObject sysObj = SystemObjectProvider.getSystemObject(javaProject);
            
			// Build call graphs using your existing CallGraphBuilder
	        List<CallGraph> callGraphs = new CallGraphBuilder(javaProject, sysObj).buildCallGraphs();
	        
	        // Initialize clustering graph (using our new ClusteringGraph with typed edges)
	        ClusteringGraph<ClassObject> clusteringGraph = new ClusteringGraph<ClassObject>();
	        for (CallGraph callGraph : callGraphs) {
	            for (ClassObject entity : callGraph.getRoot().allEntities) {
	                clusteringGraph.addVertex(entity);
	            }
	        }
	        
	        // Create associations mapping using static analysis
	        AssociationDetection associationsMapper = new AssociationDetection(sysObj);
	        // Add static association edges:
	        Set<ClassObject> vertices = new HashSet<ClassObject>();
	        vertices.addAll(clusteringGraph.getVertices());
	        for (ClassObject vertex : vertices) {
	            List<Association> associations = associationsMapper.getAssociationsOfClass(vertex);
	            for (Association association : associations) {
	                ClassObject toVertex = sysObj.getClassObject(association.getTo());
	                if (!clusteringGraph.hasEdge(vertex, toVertex)) {
	                    // Decide edge type based on static information:
	                    ClusteringGraph.EdgeType type = ClusteringGraph.EdgeType.REFERENCE;
	                    
	                    List<Annotation> toVertexAnnotations = toVertex.getAnnotations();
	                    Boolean isEmbedded = false;
	                    for(Annotation annotation : toVertexAnnotations) {
	                    	IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
	                    	if(annotationBinding.getName().equals("Embeddable")) {
	                    		isEmbedded = true;
	                    	}
	                    }
	                    Boolean isEnumerated = false;
	                    List<Annotation> fieldAnnotations = association.getFieldObject().getAnnotations();
	                    for(Annotation annotation : fieldAnnotations) {
	                    	IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
	                    	if(annotationBinding.getName().equals("Enumerated")) {
	                    		isEnumerated = true;
	                    	}
	                    }
	                    
	                    if (isEmbedded) { 
	                        type = ClusteringGraph.EdgeType.EMBEDDED;
	                    }
	                    if (isEnumerated) {
	                    	type = ClusteringGraph.EdgeType.VALUE;
	                    }
	                    clusteringGraph.addEdge(vertex, toVertex, baselineFor(type), type);
	                }
	            }
	        }
	        
	        System.out.println("Graph after static association:");
	        clusteringGraph.printGraph();
	        
//	        addEdgesForCallGraph(callGraphs, clusteringGraph, sysObj);
	        
	        // Enhance the graph: adjust weights/promote edges using dynamic coupling data
	        GraphEnhancer<ClassObject> enhancer = new GraphEnhancer<ClassObject>();
	        enhancer.enhanceGraph(clusteringGraph, callGraphs);
	        
	        System.out.println("Graph after dynamic enhancement:");
	        clusteringGraph.printGraph();
	        
	        
	        // Perform clustering on the enhanced graph
	        List<Set<ClassObject>> clusters;
	        if(strictAggregates) {
	        	StrictAggregateClustering<ClassObject> clustering = new StrictAggregateClustering<ClassObject>();
		        clusters = clustering.cluster(clusteringGraph);
	        } else {
		        LouvainClustering<ClassObject> clustering = new LouvainClustering<ClassObject>();
		        clusters = clustering.louvainClustering(clusteringGraph);	        	
	        }
	        
			
	        if(displayLogs) {
	        	displayCallGraphs(callGraphs);
	        }
	        displayClusters(clusters);
        } catch (Exception e) {
            e.printStackTrace();
            text.setText("Error: " + e.getMessage());
        }
    }
    
    private double baselineFor(ClusteringGraph.EdgeType type) {
        switch (type) {
            case EMBEDDED:
                return 1.0;
            case COUPLED:
                return 1.0;
            case REFERENCE:
                return 0.1;
            case VALUE:
            	return 2.0;
            case OWNERSHIP:
            	return 2.0;
            default:
                return 1.0;
        }
    }

//    private void addEdgesForCallGraph(List<CallGraph> callGraphs, ClusteringGraph<ClassObject> clusteringGraph, SystemObject sysObj) {
//    	
//    	for (CallGraph callGraph : callGraphs) {
//    		HashSet<ClassObject> definedClasses = new HashSet<ClassObject>();
//    		for (String createdEnity : callGraph.getRoot().createdEntities) {
//            	ClassObject classObjecOfEntity = sysObj.getClassObject(createdEnity);
//            	definedClasses.add(classObjecOfEntity);
//        	}
//        	for (String definedEntity : callGraph.getRoot().definedEntities) {
//	        	ClassObject classObjecOfEntity = sysObj.getClassObject(definedEntity);
//	        	definedClasses.add(classObjecOfEntity);
//        	}
//        	for (ClassObject definedClass1 : definedClasses) {
//        		for (ClassObject definedClass2 : definedClasses) {
//        	        if (!definedClass1.equals(definedClass2)) { // Avoid self-comparison
//        	        	if(clusteringGraph.hasEdge(definedClass1, definedClass2)) {
//        	        		clusteringGraph.addEdge(definedClass1,  definedClass2, 0.1);
//        	        	}
//        	        }
//        	    }
//        	}
//    	}
//    }
    
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
            sb.append("Created Entities: ");
            sb.append(callGraph.getRoot().createdEntities.toString()).append("\n");
            sb.append("Calls: \n");
            appendCalls(sb, callGraph.getRoot(), "  ");
            sb.append("\n");
        }
        text.setText(sb.toString());
    }
    
    private void displayClusters( List<Set<ClassObject>> clusters) {
        StringBuilder sb = new StringBuilder();
        
        for (Set<ClassObject> cluster : clusters) {
        	sb.append("Cluster:\n");
            for (ClassObject entityClass : cluster) {
            	 sb.append("\t" + ClusteringGraph.getSimpleName(entityClass.getName()) + "\n");
            }
        }
        text.append(sb.toString());
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
