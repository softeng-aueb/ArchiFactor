package gr.aueb.java.archifactor.jpa.refactoring.views;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import java.lang.reflect.InvocationTargetException;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.swt.widgets.Display;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.CompilationUnitCache;

import org.eclipse.ui.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.jdeodorant.refactoring.views.MyRefactoringWizard;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.jpa.JpaModel;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.stream.Collectors;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import gr.uom.java.ast.FieldObject;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.aueb.java.archifactor.jpa.enums.JpaJoinType;
import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.exceptions.AggregateViolationException;
import gr.aueb.java.archifactor.jpa.exceptions.CompositeKeyException;
import gr.aueb.java.archifactor.jpa.model.JoinTableInfo;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.refactoring.manipulators.UnmapJpaRelationshipsRefactoring;
import gr.aueb.java.archifactor.jpa.util.JpaAnnotationExtractorUtils;
import gr.uom.java.jdeodorant.refactoring.views.ElementChangedListener;


public class UnmapJpaRelationships extends ViewPart {
    private TableViewer tableViewer;
    private Action selectEntitiesAction;
    private Action previewAndApplyAction;
    private ComboViewer projectComboViewer;
    private ComboViewer frameworkComboViewer;
    private IJavaProject selectedProject;
    private FrameworkType selectedFramework = FrameworkType.QUARKUS;
    private SystemObject cachedSystemObject;
    private List<RelationshipInfo> detectedRelationships = new ArrayList<RelationshipInfo>();

    class ViewContentProvider implements IStructuredContentProvider {
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }
        public void dispose() {
        }
        public Object[] getElements(Object parent) {
            return detectedRelationships.toArray();
        }
    }

    class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
        public String getColumnText(Object obj, int index) {
            if (obj instanceof RelationshipInfo) {
                RelationshipInfo rel = (RelationshipInfo) obj;
                switch(index) {
                case 0:
                    return rel.getFromEntity();
                case 1:
                    return rel.getRelationshipType().getAnnotationName();
                case 2:
                    return rel.getToEntity();
                default:
                    return "";
                }
            }
            return "";
        }
        public Image getColumnImage(Object obj, int index) {
            return null;
        }
        public Image getImage(Object obj) {
            return null;
        }
    }
    
    class EntityTreeContentProvider implements ITreeContentProvider {
        private Object inputElement;

        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) inputElement;
                return map.keySet().toArray(); // Return packages as root elements
            }
            return new Object[0];
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof IPackageFragment && inputElement instanceof Map) {
                Map<IPackageFragment, List<ICompilationUnit>> map = (Map<IPackageFragment, List<ICompilationUnit>>) inputElement;
                List<ICompilationUnit> entityCUs = map.get(parentElement);
                return entityCUs != null ? entityCUs.toArray() : new Object[0];
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            if (element instanceof ICompilationUnit) {
                return ((ICompilationUnit) element).getParent();
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof IPackageFragment;
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            this.inputElement = newInput;
        }

        @Override
        public void dispose() {
        }
    }

    public UnmapJpaRelationships() {}

    public void createPartControl(Composite parent) {
        // Grid layout:
        // 1) Row 1 | Column 1: Label ("Select project:")
        // 2) Row 1 | Column 2: ComboViewer (dropdown)
        // 3) Row 2 | Column 1: Label ("Select framework:")
        // 4) Row 2 | Column 2: ComboViewer (dropdown)
        // 5) Row 3 | Columns 1+2: TableViewer (table spans both columns)
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 10;
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        container.setLayout(layout);

        // Project selection dropdown
        Label projectLabel = new Label(container, SWT.NONE);
        projectLabel.setText("Select project:");

        projectComboViewer = new ComboViewer(container, SWT.READ_ONLY);
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
        Label frameworkLabel = new Label(container, SWT.NONE);
        frameworkLabel.setText("Select framework:");

        frameworkComboViewer = new ComboViewer(container, SWT.READ_ONLY);
        frameworkComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        frameworkComboViewer.setContentProvider(ArrayContentProvider.getInstance());
        frameworkComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((FrameworkType) element).getDisplayName();
            }
        });

        frameworkComboViewer.setInput(FrameworkType.values());
        frameworkComboViewer.setSelection(new StructuredSelection(FrameworkType.QUARKUS));
        
        // Table
        tableViewer = new TableViewer(container, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        GridData tableLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        tableViewer.getControl().setLayoutData(tableLayoutData);
        tableViewer.setContentProvider(new ViewContentProvider());
        tableViewer.setLabelProvider(new ViewLabelProvider());
        tableViewer.setInput(getViewSite());
        tableViewer.getTable().setLinesVisible(true);
        tableViewer.getTable().setHeaderVisible(true);

        TableColumn fromEntityColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
        fromEntityColumn.setText("From Entity");
        fromEntityColumn.setWidth(200);
        fromEntityColumn.setResizable(true);
        
        TableColumn relationshipColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
        relationshipColumn.setText("Relationship");
        relationshipColumn.setWidth(150);
        relationshipColumn.setResizable(true);
        
        TableColumn toEntityColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
        toEntityColumn.setText("To Entity");
        toEntityColumn.setWidth(200);
        toEntityColumn.setResizable(true);

        // Add control listener to resize columns when table is resized
        tableViewer.getTable().addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                org.eclipse.swt.widgets.Table table = tableViewer.getTable();
                if (table.isDisposed()) {
                    return;
                }

                // Distribute: 40% - 20% - 40%
                int totalWidth = table.getClientArea().width;
                int col1Width = (int) (totalWidth * 0.4);
                int col2Width = (int) (totalWidth * 0.2);
                int col3Width = totalWidth - col1Width - col2Width;
                
                fromEntityColumn.setWidth(Math.max(col1Width, 200));
                relationshipColumn.setWidth(Math.max(col2Width, 150));
                toEntityColumn.setWidth(Math.max(col3Width, 200));
            }
        });

        makeActions();
        contributeToActionBars();

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

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalToolBar(bars.getToolBarManager());
    }

    private void fillLocalToolBar(IToolBarManager manager) {
        manager.add(selectEntitiesAction);
        manager.add(previewAndApplyAction);
    }

    private void makeActions() {
        selectEntitiesAction = new Action() {
            public void run() {
                selectEntities();
            }
        };
        selectEntitiesAction.setToolTipText("Select Entities");
        selectEntitiesAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
        selectEntitiesAction.setEnabled(true);

        previewAndApplyAction = new Action() {
            public void run() {
                previewAndApplyRefactoring();
            }
        };
        previewAndApplyAction.setToolTipText("Preview and Apply");
        previewAndApplyAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_DEF_VIEW));
        previewAndApplyAction.setEnabled(true);
    }

    public void setFocus() {
        tableViewer.getControl().setFocus();
    }

    public void dispose() {
        super.dispose();
        // Removed selection listener cleanup for simplified layout
    }
    
    private void onProjectSelectedBuildSystemObject() {
        Shell shell = getSite().getShell();
        
        IStructuredSelection selection = (IStructuredSelection) projectComboViewer.getSelection();
        IJavaProject project = (IJavaProject) selection.getFirstElement();
        if (selectedProject != null && selectedProject.equals(project)) {
            return;
        }
        
        selectedProject = project;
        detectedRelationships.clear();
        tableViewer.refresh();

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
                "Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
        }
    }

    private void selectEntities() {
        Shell shell = getSite().getShell();

        if (selectedProject == null || cachedSystemObject == null) {
            MessageDialog.openError(shell, "Project Not Loaded", "Project structure is not loaded. Please reselect the project and try again.");
            return;
        }

        List<ClassObject> entities = selectEntityClasses(shell, selectedProject);
        if (entities != null && !entities.isEmpty()) {
            try {
                refreshSystemObjectToMatchCurrentCode(shell);
                detectRelationships(entities);
                tableViewer.refresh();
            } catch (AggregateViolationException e) {
                MessageDialog.openError(shell, "Cannot Break Relationships", 
                    "Cannot proceed with breaking relationships:\n" + e.getMessage() + 
                    "\n\nThese properties indicate that the entities belong to the same aggregate and should not be separated.");
            } catch (CompositeKeyException e) {
                MessageDialog.openError(shell, "Composite Keys Not Supported",
                    "Cannot proceed with breaking relationships:\n" + e.getMessage());
            } catch (InterruptedException e) {
                // User cancelled - no action needed
            } catch (InvocationTargetException e) {
                MessageDialog.openError(shell, "Error Refreshing Project",
                    "Error refreshing project structure: " + e.getTargetException().getMessage());
            } catch (Exception e) {
                MessageDialog.openError(shell, "Error", "Error analyzing relationships:\n" + e.getMessage());
            }
        }
    }
    
    private void detectRelationships(List<ClassObject> selectedEntities) {
        detectedRelationships.clear();

        Set<String> selectedEntityNames = selectedEntities.stream()
            .map(ClassObject::getName)
            .collect(Collectors.toSet());

        List<ClassObject> otherEntities = new ArrayList<ClassObject>();
        ListIterator<ClassObject> classIterator = cachedSystemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (JpaModel.isEntity(classObj) && !selectedEntityNames.contains(classObj.getName())) {
                otherEntities.add(classObj);
            }
        }

        Set<String> otherEntityNames = otherEntities.stream()
            .map(ClassObject::getName)
            .collect(Collectors.toSet());

        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(cachedSystemObject);

        // 1. Detect relationships FROM selected entities TO other entities
        detectRelationshipsFromTo(selectedEntities, otherEntityNames, jpaExtractor);

        // 2. Detect relationships FROM other entities TO selected entities
        detectRelationshipsFromTo(otherEntities, selectedEntityNames, jpaExtractor);
    }

    private void detectRelationshipsFromTo(List<ClassObject> sourceEntities, Set<String> targetEntityNames, JpaAnnotationExtractorUtils jpaExtractor) {
        for (ClassObject sourceEntity : sourceEntities) {
            Iterator<FieldObject> fieldIterator = sourceEntity.getFieldIterator();
            while (fieldIterator.hasNext()) {
                // private Customer customer;  // name="customer", type.classType="Customer"
                // private List<Item> items;   // name="items", type.classType="List", type.genericType="<Item>"
                FieldObject fieldObject = fieldIterator.next();
                String fieldTypeName = fieldObject.getType().getClassType();
                String genericType = fieldObject.getType().getGenericType();
                if (genericType != null) {
                    fieldTypeName = genericType.replaceAll("[<>]", "").trim();
                }

                if (targetEntityNames.contains(fieldTypeName)) {
                    boolean isOwningSide = false;
                    JpaRelationshipType relationshipType = null;
                    for (Annotation annotation : fieldObject.getAnnotations()) {
                        String annotationName = annotation.getTypeName().getFullyQualifiedName();
                        if (JpaRelationshipType.isRelationshipType(annotationName)) {
                            relationshipType = JpaRelationshipType.fromAnnotationName(annotationName);
                            //checkForDangerousCascading(sourceEntity, fieldObject, annotation);
                        } else if (JpaJoinType.isJoinType(annotationName)) {
                            isOwningSide = true;
                        }
                    }

                    if (relationshipType != null) {
                        String joinColumnName = jpaExtractor.extractJoinColumnName(fieldObject);
                        String originPkType = jpaExtractor.extractIdFieldType(sourceEntity.getName());
                        String referencedPkType = jpaExtractor.extractIdFieldType(fieldTypeName);
                        String referencedPkName = jpaExtractor.extractIdFieldName(fieldTypeName);
                        String joinTableName = null;
                        String joinTableJoinColumns = null;
                        String joinTableInverseJoinColumns = null;
                        if (relationshipType == JpaRelationshipType.MANY_TO_MANY) {
                            JoinTableInfo joinTableInfo = jpaExtractor.extractJoinTableInfo(fieldObject);
                            joinTableName = joinTableInfo.getTableName();
                            joinTableJoinColumns = joinTableInfo.getJoinColumns();
                            joinTableInverseJoinColumns = joinTableInfo.getInverseJoinColumns();
                        }
                        RelationshipInfo relInfo = RelationshipInfo.builder()
                            .fromEntity(sourceEntity.getName())
                            .relationshipType(relationshipType)
                            .toEntity(fieldTypeName)
                            .isOwningSide(isOwningSide)
                            .joinColumnName(joinColumnName)
                            .fieldName(fieldObject.getName())
                            .originPkType(originPkType)
                            .referencedPkType(referencedPkType)
                            .referencedPkName(referencedPkName)
                            .joinTableName(joinTableName)
                            .joinTableJoinColumns(joinTableJoinColumns)
                            .joinTableInverseJoinColumns(joinTableInverseJoinColumns)
                            .build();
                        detectedRelationships.add(relInfo);
                    }
                }
            }
        }
    }

    private void checkForDangerousCascading(ClassObject entity, FieldObject field, Annotation annotation) {
        if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            for (Object obj : normalAnnotation.values()) {
                MemberValuePair pair = (MemberValuePair) obj;
                String propertyName = pair.getName().getIdentifier();
                String propertyValue = pair.getValue().toString();
                if (propertyName.equals("cascade")) {
                    if (propertyValue.contains("CascadeType.PERSIST") || propertyValue.contains("CascadeType.ALL")) {
                        throw new AggregateViolationException("Entity " + entity.getName() + "." + field.getName() + " has: " + propertyName + " = " + propertyValue);
                    }
                } else if (propertyName.equals("orphanRemoval")) {
                    if (propertyValue.equals("true")) {
                        throw new AggregateViolationException("Entity " + entity.getName() + "." + field.getName() + " has: orphanRemoval=true");
                    }
                }
            }
        }
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
    
    private List<ClassObject> selectEntityClasses(Shell shell, IJavaProject project) {
        try {
            List<ClassObject> entities = findEntityClasses();

            if (entities.isEmpty()) {
                MessageDialog.openInformation(shell, "No Entities Found", "No classes annotated with @Entity were found in the selected project.");
                return null;
            }

            Map<IPackageFragment, List<ICompilationUnit>> entitiesByPackage = groupEntitiesByPackage(entities);

            CheckedTreeSelectionDialog dialog = new CheckedTreeSelectionDialog(
                shell,
                new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT),
                new EntityTreeContentProvider()
            );

            dialog.setInput(entitiesByPackage);
            dialog.setTitle("Select JPA Entity Classes");
            dialog.setMessage("Select the JPA entity classes to analyze for relationship unmapping:");
            dialog.setContainerMode(true);

            if (dialog.open() == Window.OK) {
                Object[] result = dialog.getResult();
                List<ClassObject> selectedEntities = new ArrayList<ClassObject>();
                for (Object obj : result) {
                    if (obj instanceof ICompilationUnit) {
                        ICompilationUnit cu = (ICompilationUnit) obj;
                        Set<ClassObject> classObjs = cachedSystemObject.getClassObjects(cu);
                        for (ClassObject classObj : classObjs) {
                            if (JpaModel.isEntity(classObj)) {
                                selectedEntities.add(classObj);
                            }
                        }
                    }
                }
                return selectedEntities;
            }
        } catch (Exception e) {
            MessageDialog.openError(shell, "Error", "Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private List<ClassObject> findEntityClasses() {
        List<ClassObject> entities = new ArrayList<ClassObject>();
        ListIterator<ClassObject> classIterator = cachedSystemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (JpaModel.isEntity(classObj)) {
                entities.add(classObj);
            }
        }
        return entities;
    }

    private Map<IPackageFragment, List<ICompilationUnit>> groupEntitiesByPackage(List<ClassObject> entities) {
        Map<IPackageFragment, List<ICompilationUnit>> entitiesByPackage = new HashMap<IPackageFragment, List<ICompilationUnit>>();
        for (ClassObject entity : entities) {
            ICompilationUnit cu = (ICompilationUnit) entity.getITypeRoot();
            IPackageFragment pkg = (IPackageFragment) cu.getParent();
            if (!entitiesByPackage.containsKey(pkg)) {
                entitiesByPackage.put(pkg, new ArrayList<ICompilationUnit>());
            }
            entitiesByPackage.get(pkg).add(cu);
        }
        return entitiesByPackage;
    }

    private void previewAndApplyRefactoring() {
        Shell shell = getSite().getShell();

        if (selectedProject == null || cachedSystemObject == null) {
            MessageDialog.openError(shell, "Project Not Loaded", "Project structure is not loaded. Please reselect the project and try again.");
            return;
        }

        if (detectedRelationships.isEmpty()) {
            MessageDialog.openWarning(shell, "No Relationships", "No relationships detected. Please select entities first.");
            return;
        }

        try {
            refreshSystemObjectToMatchCurrentCode(shell);

            UnmapJpaRelationshipsRefactoring refactoring = new UnmapJpaRelationshipsRefactoring(selectedProject, detectedRelationships, cachedSystemObject, selectedFramework);
            MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, null);
            RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
            int status = operation.run(shell, "Unmap JPA Relationships");
            if (wizard.getShell() != null && !wizard.getShell().isDisposed()) {
                wizard.getShell().setMaximized(true);
            }

            if (status == RefactoringStatus.OK) {
                forceRebuildSystemObject();
            }
        } catch (InterruptedException e) {
            // User cancelled - no action needed
        } catch (InvocationTargetException e) {
            MessageDialog.openError(shell, "Error Refreshing Project",
                "Error refreshing project structure: " + e.getTargetException().getMessage());
        } catch (Exception e) {
            MessageDialog.openError(shell, "Refactoring Error", "An error occurred during refactoring: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void refreshSystemObjectToMatchCurrentCode(Shell shell) throws InterruptedException, InvocationTargetException {
        IWorkbench wb = PlatformUI.getWorkbench();
        IProgressService ps = wb.getProgressService();
        ps.busyCursorWhile(new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                try {
                    new ASTReader(selectedProject, cachedSystemObject, monitor);
                    cachedSystemObject = ASTReader.getSystemObject();
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

    /**
     * Forces a complete rebuild of the SystemObject from scratch.
     *
     * This is necessary after applying refactorings because:
     * 1. The cachedSystemObject contains stale AST node references from before the refactoring
     * 2. The CompilationUnitCache contains cached parsed AST trees from before the refactoring
     *
     * If compilation errors are introduced by the refactoring, we set cachedSystemObject to null,
     * forcing the user to reselect the project after fixing the errors.
     */
    private void forceRebuildSystemObject() throws InterruptedException, InvocationTargetException {
        IWorkbench wb = PlatformUI.getWorkbench();
        IProgressService ps = wb.getProgressService();
        ps.busyCursorWhile(new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                try {
                    CompilationUnitCache.getInstance().clearAffectedCompilationUnits();
                    CompilationUnitCache.getInstance().clearCache();
                    new ASTReader(selectedProject, monitor);
                    cachedSystemObject = ASTReader.getSystemObject();
                } catch (CompilationErrorDetectedException e) {
                	cachedSystemObject = null;
                }
            }
        });
    }
}