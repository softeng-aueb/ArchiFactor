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
import java.util.List;
import java.util.Set;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;
    private ComboViewer projectComboViewer;
    private int createWeight;
    private int writeWeight;
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

        // Helper method to create a smaller Text field
        // Returns a new Text widget with a set widthHint
        final int TEXT_FIELD_WIDTH = 50;
        GridData gdSmallText = new GridData(SWT.FILL, SWT.CENTER, false, false);
        gdSmallText.widthHint = TEXT_FIELD_WIDTH;

        // Row 2: "Create Weight" label + text field
        Label createWeightLabel = new Label(parent, SWT.NONE);
        createWeightLabel.setText("Create Weight:");

        final Text createWeightText = new Text(parent, SWT.BORDER);
        createWeightText.setLayoutData(gdSmallText);

        // Row 3: "Read Weight" label + text field
        Label readWeightLabel = new Label(parent, SWT.NONE);
        readWeightLabel.setText("Read Weight:");

        final Text readWeightText = new Text(parent, SWT.BORDER);
        readWeightText.setLayoutData(gdSmallText);

        // Row 4: "Write Weight" label + text field
        Label writeWeightLabel = new Label(parent, SWT.NONE);
        writeWeightLabel.setText("Write Weight:");

        final Text writeWeightText = new Text(parent, SWT.BORDER);
        writeWeightText.setLayoutData(gdSmallText);

        // Row 5: Centered "Run" button spanning 2 columns
        Button runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Run Aggregation Identification");
        GridData buttonGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        runButton.setLayoutData(buttonGridData);

        // 3) Assign text fields to class fields on click
        runButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
			    try {
			        createWeight = Integer.parseInt(createWeightText.getText());
			    } catch (NumberFormatException e) {
			        createWeight = 5;
			    }
			    try {
			        readWeight = Integer.parseInt(readWeightText.getText());
			    } catch (NumberFormatException e) {
			        readWeight = 1;
			    }
			    try {
			        writeWeight = Integer.parseInt(writeWeightText.getText());
			    } catch (NumberFormatException e) {
			        writeWeight = 3;
			    }

			    // Now run your method, using the just-updated fields
			    runAggregationIdentification();
			}
		});

        // Row 6: Text area (spanning 2 columns)
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
        GridData textLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
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
			Set<ClassObject> vertices = new HashSet<ClassObject>();
			vertices.addAll(clusteringGraph.getVertices());
			for (ClassObject vertex : vertices) {
				List<Association> associations = associationsMapper.getAssociationsOfClass(vertex);
				for (Association association : associations) {
					ClassObject toVertex = sysObj.getClassObject(association.getTo());
					clusteringGraph.addEdge(vertex,  toVertex, 1.0);
				}
			}
			
			// Add edges for call graphs
			addEdgesForCallGraph(callGraphs, clusteringGraph, sysObj);

        	LouvainClustering<ClassObject> louvain = new LouvainClustering<ClassObject>();
        	List<Set<ClassObject>> clusters = louvain.louvainClustering(clusteringGraph);

        	for (Set<ClassObject> cluster : clusters) {
        	    System.out.println("Cluster: ");
        	    for (ClassObject entityClass : cluster) {
        	    	System.out.println(entityClass.getName());
        	    }
        	}
        	 
            displayCallGraphs(callGraphs);
        } catch (Exception e) {
            e.printStackTrace();
            text.setText("Error: " + e.getMessage());
        }
    }

    private void addEdgesForCallGraph(List<CallGraph> callGraphs, ClusteringGraph<ClassObject> clusteringGraph, SystemObject sysObj) {
        for (CallGraph callGraph : callGraphs) {
            if (callGraph.getRoot().createdEntities.size() != 0) {
                HashSet<ClassObject> createdEntities = new HashSet<ClassObject>();
            	for (String createdEnity : callGraph.getRoot().createdEntities) {
                	ClassObject classObjecOfEntity = sysObj.getClassObject(createdEnity);
                	createdEntities.add(classObjecOfEntity);
            	}
            	HashSet<ClassObject> otherEntities = new HashSet<ClassObject>();
            	for (String accessedEntity : callGraph.getRoot().accessedEntities) {
                	ClassObject classObjecOfEntity = sysObj.getClassObject(accessedEntity);
                	otherEntities.add(classObjecOfEntity);
            	}
            	for (String definedEntity : callGraph.getRoot().definedEntities) {
                	ClassObject classObjecOfEntity = sysObj.getClassObject(definedEntity);
                	otherEntities.add(classObjecOfEntity);
            	}
            	// add edges
            	for (ClassObject createdEntity : createdEntities) {
            		for(ClassObject otherEntity : otherEntities) {
            			clusteringGraph.addEdge(createdEntity,  otherEntity, createWeight);
            		}
            	}
            }
            
            else if (callGraph.getRoot().definedEntities.size() != 0) {
            	 HashSet<ClassObject> definedEntities = new HashSet<ClassObject>();
             	for (String definedEntity : callGraph.getRoot().definedEntities) {
                 	ClassObject classObjecOfEntity = sysObj.getClassObject(definedEntity);
                 	definedEntities.add(classObjecOfEntity);
             	}
             	HashSet<ClassObject> otherEntities = new HashSet<ClassObject>();
             	for (String accessedEntity : callGraph.getRoot().accessedEntities) {
                 	ClassObject classObjecOfEntity = sysObj.getClassObject(accessedEntity);
                 	otherEntities.add(classObjecOfEntity);
             	}
             	// add edges
             	for (ClassObject definedEntity : definedEntities) {
             		for(ClassObject otherEntity : otherEntities) {
             			clusteringGraph.addEdge(definedEntity,  otherEntity, writeWeight);
             		}
             	}
            }
            
            else if (callGraph.getRoot().accessedEntities.size() != 0) {
            	 HashSet<ClassObject> accessedEntities = new HashSet<ClassObject>();
              	for (String accessedEntity : callGraph.getRoot().accessedEntities) {
                  	ClassObject classObjecOfEntity = sysObj.getClassObject(accessedEntity);
                  	accessedEntities.add(classObjecOfEntity);
              	}
              	// add edges
              	for (ClassObject accessedEntity : accessedEntities) {
              		for(ClassObject accessedEntity1 : accessedEntities) {
              			clusteringGraph.addEdge(accessedEntity,  accessedEntity1, readWeight);
              		}
              	}
            }
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
            sb.append("Created Entities: ");
            sb.append(callGraph.getRoot().createdEntities.toString()).append("\n");
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
