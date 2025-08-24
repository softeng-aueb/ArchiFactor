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
import gr.aueb.java.ddd.aggregatesIdentification.SystemObjectProvider;
import gr.aueb.java.jpa.JpaModel;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;


public class UnmapJpaRelationships extends ViewPart {
	private TableViewer tableViewer;
	private Action selectEntitiesAction;
	private Action previewAndApplyAction;
	private Action doubleClickAction;
	private ComboViewer projectComboViewer;
	private IJavaProject selectedProject;
	private List<ClassObject> selectedEntities = new ArrayList<ClassObject>();

	class ViewContentProvider implements IStructuredContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}
		public void dispose() {
		}
		public Object[] getElements(Object parent) {
			// Return empty array for now - no data to populate
			return new Object[] {};
		}
	}

	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			// Return empty strings for now - no data to display
			switch(index) {
			case 0:
				return ""; // From Entity
			case 1:
				return ""; // Relationship
			case 2:
				return ""; // To Entity
			default:
				return "";
			}
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
		
		TableLayout tableLayout = new TableLayout();
		tableLayout.addColumnData(new ColumnWeightData(33, true));
		tableLayout.addColumnData(new ColumnWeightData(34, true));
		tableLayout.addColumnData(new ColumnWeightData(33, true));
		tableViewer.getTable().setLayout(tableLayout);
		
		new TableColumn(tableViewer.getTable(), SWT.LEFT).setText("From Entity");
		new TableColumn(tableViewer.getTable(), SWT.LEFT).setText("Relationship");
		new TableColumn(tableViewer.getTable(), SWT.LEFT).setText("To Entity");
		
		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				// Selection changed - could be used for future functionality
			}
		});

		for (int i = 0, n = tableViewer.getTable().getColumnCount(); i < n; i++) {
			tableViewer.getTable().getColumn(i).pack();
		}

		tableViewer.getTable().setLinesVisible(true);
		tableViewer.getTable().setHeaderVisible(true);
		makeActions();
		hookDoubleClickAction();
		contributeToActionBars();
		// Removed selection listeners for simplified layout
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
	
	private void selectEntities() {
		Shell shell = getSite().getShell();

		IStructuredSelection selection = (IStructuredSelection) projectComboViewer.getSelection();
		if (selection.isEmpty()) {
			MessageDialog.openInformation(shell, "No Project Selected", "Please select a project first.");
			return;
		}

		IJavaProject project = (IJavaProject) selection.getFirstElement();
		selectedProject = project;

		List<ClassObject> entities = selectEntityClasses(shell, project);
		if (entities != null && !entities.isEmpty()) {
			selectedEntities = entities;
			for (ClassObject entity : selectedEntities) {
				System.out.println(entity.getName());
			}
			// TODO: Update the table viewer to show selected entities
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

				// Now convert selected ICompilationUnits to ClassObjects using SystemObject
				SystemObject systemObject = SystemObjectProvider.getSystemObject(project);
				if (systemObject == null) {
					MessageDialog.openError(shell, "Error", "Could not analyze project structure for selected entities.");
					return null;
				}

				List<ClassObject> selectedEntities = new ArrayList<ClassObject>();
				for (Object obj : result) {
					if (obj instanceof ICompilationUnit) {
						ICompilationUnit cu = (ICompilationUnit) obj;
						Set<ClassObject> classObjs = systemObject.getClassObjects(cu);
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