package gr.aueb.java.archifactor.refactoring.views;

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
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.ui.*;
import org.eclipse.swt.SWT;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.jpa.JpaModel;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IAnnotation;
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


public class UnmapJpaRelationships extends ViewPart {
	private TableViewer tableViewer;
	private Action selectEntitiesAction;
	private Action previewAndApplyAction;
	private Action doubleClickAction;
	private ComboViewer projectComboViewer;
	private IJavaProject selectedProject;
	private SystemObject cachedSystemObject;
	private List<ClassObject> selectedEntities = new ArrayList<ClassObject>();
	private List<RelationshipInfo> detectedRelationships = new ArrayList<RelationshipInfo>();

	class AggregateViolationException extends RuntimeException {
		public AggregateViolationException(String message) {
			super(message);
		}
	}

	class RelationshipInfo {
		private String fromEntity;
		private String relationshipType;
		private String toEntity;
		private boolean isOwningSide;
		
		public RelationshipInfo(String fromEntity, String relationshipType, String toEntity, boolean isOwningSide) {
			this.fromEntity = fromEntity;
			this.relationshipType = relationshipType;
			this.toEntity = toEntity;
			this.isOwningSide = isOwningSide;
		}
		
		public String getFromEntity() {
			return fromEntity;
		}

		public String getRelationshipType() {
			return relationshipType;
		}

		public String getToEntity() {
			return toEntity;
		}
		
		public boolean isOwningSide() {
			return isOwningSide;
		}
	}

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
					return rel.getRelationshipType();
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

	class NameSorter extends ViewerSorter {
		public int compare(Viewer viewer, Object obj1, Object obj2) {
			// No sorting needed for now - return 0
			return 0;
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
        // 3) Row 2 | Columns 1+2: TableViewer (table spans both columns)
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
		
		// Table
		tableViewer = new TableViewer(container, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		GridData tableLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		tableViewer.getControl().setLayoutData(tableLayoutData);
		tableViewer.setContentProvider(new ViewContentProvider());
		tableViewer.setLabelProvider(new ViewLabelProvider());
		tableViewer.setSorter(new NameSorter());
		tableViewer.setInput(getViewSite());

		TableColumn fromEntityColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
		fromEntityColumn.setText("From Entity");
		fromEntityColumn.setWidth(200);
		
		TableColumn relationshipColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
		relationshipColumn.setText("Relationship");
		relationshipColumn.setWidth(150);
		
		TableColumn toEntityColumn = new TableColumn(tableViewer.getTable(), SWT.LEFT);
		toEntityColumn.setText("To Entity");
		toEntityColumn.setWidth(200);

		TableLayout tableLayout = new TableLayout();
		tableLayout.addColumnData(new ColumnWeightData(40, 150));
		tableLayout.addColumnData(new ColumnWeightData(20, 100));
		tableLayout.addColumnData(new ColumnWeightData(40, 150));
		tableViewer.getTable().setLayout(tableLayout);

		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				// Selection changed - could be used for future functionality
			}
		});

		tableViewer.getTable().setLinesVisible(true);
		tableViewer.getTable().setHeaderVisible(true);
		makeActions();
		hookDoubleClickAction();
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
				System.out.println("Preview & Apply action clicked - placeholder");
				// TODO: Implement preview and apply functionality
			}
		};
		previewAndApplyAction.setToolTipText("Preview & Apply");
		previewAndApplyAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_DEF_VIEW));
		previewAndApplyAction.setEnabled(true);

		doubleClickAction = new Action() {
			public void run() {
				System.out.println("Double click action - placeholder");
				// TODO: Implement double click functionality
			}
		};
	}

	private void hookDoubleClickAction() {
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
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
		selectedEntities.clear();
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
			selectedEntities = entities;
			
			try {
				detectRelationships();
				tableViewer.refresh();
			} catch (AggregateViolationException e) {
				MessageDialog.openError(shell, "Cannot Break Relationships", 
					"Cannot proceed with breaking relationships:\n" + e.getMessage() + 
					"\n\nThese properties indicate that the entities belong to the same aggregate and should not be separated.");
			} catch (Exception e) {
				MessageDialog.openError(shell, "Error", "Error analyzing relationships:\n" + e.getMessage());
			}
		}
	}
	
	private void detectRelationships() {
		detectedRelationships.clear();
		
		Set<String> allEntityNames = new HashSet<String>();
		ListIterator<ClassObject> classIterator = cachedSystemObject.getClassListIterator();
		while (classIterator.hasNext()) {
			ClassObject classObj = classIterator.next();
			if (JpaModel.isEntity(classObj)) {
				allEntityNames.add(classObj.getName());
			}
		}

		Set<String> selectedEntityNames = selectedEntities.stream()
			.map(ClassObject::getName)
			.collect(Collectors.toSet());

		Set<String> otherEntityNames = new HashSet<String>(allEntityNames);
		otherEntityNames.removeAll(selectedEntityNames);
		
		// 1. Detect relationships FROM selected entities TO other entities
		for (ClassObject selectedEntity : selectedEntities) {
			Iterator<FieldObject> fieldIterator = selectedEntity.getFieldIterator();
			while (fieldIterator.hasNext()) {
				// private Customer customer;  // name="customer", type.classType="Customer"
				// private List<Item> items;   // name="items", type.classType="List", type.genericType="<Item>"
				FieldObject fieldObject = fieldIterator.next();
				String fieldTypeName = fieldObject.getType().getClassType();
				String genericType = fieldObject.getType().getGenericType();
				if (genericType != null) {
					fieldTypeName = genericType.replaceAll("[<>]", "").trim();
				}

				if (otherEntityNames.contains(fieldTypeName)) {
					boolean isOwningSide = false;
					String relationshipType = null;
					for (Annotation annotation : fieldObject.getAnnotations()) {
						String annotationType = annotation.getTypeName().getFullyQualifiedName();
						if (isJpaRelationshipAnnotation(annotationType)) {
							relationshipType = annotationType;
							checkForDangerousCascading(selectedEntity, fieldObject, annotation);
						} else if (isJoinAnnotation(annotationType)) {
							isOwningSide = true;
						}
					}

					if (relationshipType != null) {
						detectedRelationships.add(
							new RelationshipInfo(selectedEntity.getName(), relationshipType, fieldTypeName, isOwningSide));
					}
				}
			}
		}
		
		// 2. Detect relationships FROM other entities TO selected entities
		ListIterator<ClassObject> allClassIterator = cachedSystemObject.getClassListIterator();
		while (allClassIterator.hasNext()) {
			ClassObject otherEntity = allClassIterator.next();
			if (selectedEntityNames.contains(otherEntity.getName()) || !JpaModel.isEntity(otherEntity)) {
				continue;
			}
			
			Iterator<FieldObject> fieldIterator = otherEntity.getFieldIterator();
			while (fieldIterator.hasNext()) {
				// private Customer customer;  // name="customer", type.classType="Customer"
				// private List<Item> items;   // name="items", type.classType="List", type.genericType="<Item>"
				FieldObject fieldObject = fieldIterator.next();
				String fieldTypeName = fieldObject.getType().getClassType();
				String genericType = fieldObject.getType().getGenericType();
				if (genericType != null) {
					fieldTypeName = genericType.replaceAll("[<>]", "").trim();
				}

				if (selectedEntityNames.contains(fieldTypeName)) {
					boolean isOwningSide = false;
					String relationshipType = null;
					for (Annotation annotation : fieldObject.getAnnotations()) {
						String annotationType = annotation.getTypeName().getFullyQualifiedName();
						if (isJpaRelationshipAnnotation(annotationType)) {
							relationshipType = annotationType;
							checkForDangerousCascading(otherEntity, fieldObject, annotation);
						} else if (isJoinAnnotation(annotationType)) {
							isOwningSide = true;
						}
					}

					if (relationshipType != null) {
						detectedRelationships.add(
							new RelationshipInfo(otherEntity.getName(), relationshipType, fieldTypeName, isOwningSide));
					}
				}
			}
		}
	}

	private boolean isJpaRelationshipAnnotation(String annotationType) {
		return annotationType.equals("OneToMany") || 
			   annotationType.equals("ManyToOne") || 
			   annotationType.equals("OneToOne") || 
			   annotationType.equals("ManyToMany");
	}

	private boolean isJoinAnnotation(String annotationType) {
		return annotationType.equals("JoinColumn") || 
			   annotationType.equals("JoinTable");
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
			List<ICompilationUnit> entities = findEntitiesUsingJDT(project);
			
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
		} catch (JavaModelException e) {
			MessageDialog.openError(shell, "Error", "Error analyzing project: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			MessageDialog.openError(shell, "Error", "Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private List<ICompilationUnit> findEntitiesUsingJDT(IJavaProject project) throws JavaModelException {
		List<ICompilationUnit> entities = new ArrayList<ICompilationUnit>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				for (IJavaElement element : root.getChildren()) {
					if (element instanceof IPackageFragment) {
						IPackageFragment packageFragment = (IPackageFragment) element;
						for (ICompilationUnit cu : packageFragment.getCompilationUnits()) {
							IType mainType = cu.findPrimaryType();
							if (mainType != null && hasEntityAnnotation(mainType)) {
								entities.add(cu);
							}
						}
					}
				}
			}
		}
		return entities;
	}

	private boolean hasEntityAnnotation(IType type) throws JavaModelException {
		for (IAnnotation annotation : type.getAnnotations()) {
			String annotationName = annotation.getElementName();
			if (annotationName.contains("Entity")) {
				return true;
			}
		}
		return false;
	}

	private Map<IPackageFragment, List<ICompilationUnit>> groupEntitiesByPackage(List<ICompilationUnit> entities) {
		Map<IPackageFragment, List<ICompilationUnit>> entitiesByPackage = new HashMap<IPackageFragment, List<ICompilationUnit>>();
		for (ICompilationUnit entity : entities) {
			IPackageFragment pkg = (IPackageFragment) entity.getParent();
			if (!entitiesByPackage.containsKey(pkg)) {
				entitiesByPackage.put(pkg, new ArrayList<ICompilationUnit>());
			}
			entitiesByPackage.get(pkg).add(entity);
		}
		return entitiesByPackage;
	}
}