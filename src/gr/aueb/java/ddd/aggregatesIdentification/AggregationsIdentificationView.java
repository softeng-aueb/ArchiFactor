package gr.aueb.java.ddd.aggregatesIdentification;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.association.Association;
import gr.uom.java.ast.association.AssociationDetection;
import gr.uom.java.jdeodorant.refactoring.views.ElementChangedListener;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IProgressService;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private ComboViewer projectComboViewer;
    private IJavaProject selectedProject;
    private SystemObject cachedSystemObject;
    private ComboViewer frameworkComboViewer;
    private FrameworkType selectedFramework = FrameworkType.QUARKUS;
    private Boolean strictAggregates;
    private Boolean displayLogs;
    private Text text;

    @Override
    public void createPartControl(Composite parent) {
        // Grid layout:
        // 1) Row 1 | Column 1: Label ("Select project:")
        // 2) Row 1 | Column 2: ComboViewer (dropdown)
        // 3) Row 2 | Columns 1+2: Strict Aggregates checkbox
        // 4) Row 3 | Columns 1+2: Display Logs checkbox
        // 5) Row 4 | Columns 1+2: Run button
        // 6) Row 5 | Columns 1+2: Text area
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 10;
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        parent.setLayout(layout);

        // Project selection dropdown
        Label projectLabel = new Label(parent, SWT.NONE);
        projectLabel.setText("Select project:");

        projectComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
        projectComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());
        projectComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((IJavaProject) element).getElementName();
            }
        });

        populateProjectCombo();

        // Framework selection dropdown
        Label frameworkLabel = new Label(parent, SWT.NONE);
        frameworkLabel.setText("Select framework:");

        frameworkComboViewer = new ComboViewer(parent, SWT.READ_ONLY);
        frameworkComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        frameworkComboViewer.setContentProvider(ArrayContentProvider.getInstance());
        frameworkComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((FrameworkType) element).getDisplayName();
            }
        });
        frameworkComboViewer.setInput(FrameworkType.values());
        frameworkComboViewer.setSelection(new StructuredSelection(selectedFramework));

        // Strict Aggregates checkbox
        final Button strictCheck = new Button(parent, SWT.CHECK);
        strictCheck.setText("Use Strict Aggregates");
        strictCheck.setSelection(false);
        GridData strictCheckGD = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        strictCheck.setLayoutData(strictCheckGD);

        // Display Logs checkbox
        final Button logsCheck = new Button(parent, SWT.CHECK);
        logsCheck.setText("Display Logs");
        logsCheck.setSelection(false);
        GridData logsCheckGD = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        logsCheck.setLayoutData(logsCheckGD);

        // Run button
        Button runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Run Aggregation Identification");
        GridData buttonGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        runButton.setLayoutData(buttonGridData);
        runButton.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
            	strictAggregates = strictCheck.getSelection();
                displayLogs = logsCheck.getSelection();
                runAggregationIdentification();
            }
        });

        // Text area
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
        GridData textLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        text.setLayoutData(textLayoutData);

        // Do the heavy-lifting of creating the SystemObject in the beginning
        if (!projectComboViewer.getSelection().isEmpty()) {
            onProjectSelectedBuildSystemObject();
        }

        projectComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                onProjectSelectedBuildSystemObject();
            }
        });

        frameworkComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                if (!selection.isEmpty()) {
                    selectedFramework = (FrameworkType) selection.getFirstElement();
                }
            }
        });

        JavaCore.addElementChangedListener(ElementChangedListener.getInstance());
    }
    

    private void populateProjectCombo() {
        try {
            List<IJavaProject> projects = new ArrayList<IJavaProject>();
            for (IJavaProject project : JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects()) {
                projects.add(project);
            }

            projectComboViewer.setInput(projects);
            if (!projects.isEmpty()) {
                projectComboViewer.getCombo().select(0);
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
    }

    private void onProjectSelectedBuildSystemObject() {
        Shell shell = getSite().getShell();

        IStructuredSelection selection = (IStructuredSelection) projectComboViewer.getSelection();
        IJavaProject project = (IJavaProject) selection.getFirstElement();
        if (selectedProject != null && selectedProject.equals(project)) {
            return;
        }

        selectedProject = project;

        try {
            IWorkbench wb = PlatformUI.getWorkbench();
            IProgressService ps = wb.getProgressService();
            if (ASTReader.getSystemObject() != null && project.equals(ASTReader.getExaminedProject())) {
                new ASTReader(project, ASTReader.getSystemObject(), null);
            } else {
                ps.busyCursorWhile(new IRunnableWithProgress() {
                    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                        try {
                            new ASTReader(project, monitor);
                        } catch (CompilationErrorDetectedException e) {
                            Display.getDefault().asyncExec(new Runnable() {
                                public void run() {
                                    MessageDialog.openInformation(shell, "Compilation Errors",
                                        "Compilation errors were detected in the project. Fix the errors before using this feature.");
                                }
                            });
                        }
                    }
                });
            }
            cachedSystemObject = ASTReader.getSystemObject();
        } catch (InterruptedException e) {
            cachedSystemObject = null;
        } catch (InvocationTargetException e) {
            cachedSystemObject = null;
            MessageDialog.openError(shell, "Error Loading Project",
                "Error loading project structure: " + e.getTargetException().getMessage());
        } catch (CompilationErrorDetectedException e) {
            cachedSystemObject = null;
            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Compilation Errors",
                "Compilation errors were detected in the project. Fix the errors before using this feature.");
        }
    }

    private void runAggregationIdentification() {
        if (selectedProject == null || cachedSystemObject == null) {
            MessageDialog.openError(getSite().getShell(), "Project Not Loaded", "Project structure is not loaded. Please reselect the project and try again.");
            return;
        }

        try {
        	BaseCallGraphBuilder callGraphBuilder = CallGraphBuilderFactory.create(selectedFramework, selectedProject, cachedSystemObject);
	        List<CallGraph> callGraphs = callGraphBuilder.buildCallGraphs();

	        // Initialize clustering graph (using our new ClusteringGraph with typed edges)
	        ClusteringGraph<ClassObject> clusteringGraph = new ClusteringGraph<ClassObject>();
	        for (CallGraph callGraph : callGraphs) {
	            for (ClassObject entity : callGraph.getRoot().allEntitiesObjects) {
	                clusteringGraph.addVertex(entity);
	            }
	        }
	        
	        // Create associations mapping using static analysis
	        AssociationDetection associationsMapper = new AssociationDetection(cachedSystemObject);
	        // Add static association edges:
	        Set<ClassObject> vertices = new HashSet<ClassObject>();
	        vertices.addAll(clusteringGraph.getVertices());
	        for (ClassObject vertex : vertices) {
	            List<Association> associations = associationsMapper.getAssociationsOfClass(vertex);
	            for (Association association : associations) {
	                ClassObject toVertex = cachedSystemObject.getClassObject(association.getTo());
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
	                    	if(
	                    		annotationBinding.getName().equals("Enumerated") ||
	                    		annotationBinding.getName().equals("Type")
	                    	) {
	                    		isEnumerated = true;
	                    	}
	                    }
	                    
	                    if (isEmbedded) { 
	                        type = ClusteringGraph.EdgeType.EMBEDDED;
	                    }
	                    if (isEnumerated) {
	                    	type = ClusteringGraph.EdgeType.VALUE;
	                    }
	                    clusteringGraph.addEdge(vertex, toVertex, ClusteringGraph.baselineFor(type), type);
	                }
	            }
	        }

	        StringBuilder graphString = new StringBuilder();
	        graphString.append("\n\n Graph after static association:\n");
	        graphString.append(clusteringGraph.printGraph());
	        text.append(graphString.toString());
	        
	        
	        // Enhance the graph: adjust weights/promote edges using dynamic coupling data
	        GraphEnhancer<ClassObject> enhancer = new GraphEnhancer<ClassObject>();
	        enhancer.enhanceGraph(clusteringGraph, callGraphs);
	        
	        StringBuilder graphString2 = new StringBuilder();
	        graphString2.append("\n\n Graph after enhancement:\n");
	        graphString2.append(clusteringGraph.printGraph());
	        text.append(graphString2.toString());
	        
	        
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
    
    
    private void displayCallGraphs(List<CallGraph> callGraphs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n Callgraphs:\n");
        for (CallGraph callGraph : callGraphs) {
            sb.append("Endpoint: ").append(callGraph.getRoot().getMethodName());
            if(callGraph.getRoot().isReadOnly()) {
            	sb.append(" [ReadOnly]");
            }
            if(callGraph.getRoot().isTransactional) {
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
        text.append(sb.toString());
    }
    
    private void displayClusters( List<Set<ClassObject>> clusters) {
        StringBuilder sb = new StringBuilder();
        
        for (Set<ClassObject> cluster : clusters) {
        	sb.append("\nCluster:\n");
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
            	if(calledMethod.isReadOnly()) {
            		sb.append(" [ReadOnly]");
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
