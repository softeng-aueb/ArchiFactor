package gr.aueb.java.archifactor.refactoring.views;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.ui.texteditor.ITextEditor;

import gr.aueb.java.archifactor.refactoring.manipulators.BreakAssociationRefactoring;
import gr.aueb.java.jpa.AssociationObject;
import gr.aueb.java.jpa.EntityObject;
import gr.aueb.java.jpa.JpaModel;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.Access;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.CompilationUnitCache;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.distance.CandidateRefactoring;
import gr.uom.java.distance.DistanceMatrix;
import gr.uom.java.distance.Entity;
import gr.uom.java.distance.ExtractClassCandidateGroup;
import gr.uom.java.distance.ExtractClassCandidateRefactoring;
import gr.uom.java.distance.ExtractedConcept;
import gr.uom.java.distance.MySystem;
import gr.uom.java.jdeodorant.preferences.PreferenceConstants;
import gr.uom.java.jdeodorant.refactoring.Activator;
import gr.uom.java.jdeodorant.refactoring.manipulators.MoveClassRefactoring;
import gr.uom.java.jdeodorant.refactoring.views.CodeSmellPackageExplorer;
import gr.uom.java.jdeodorant.refactoring.views.CodeSmellPackageExplorer.CodeSmellType;
import gr.uom.java.jdeodorant.refactoring.views.CodeSmellVisualization;
import gr.uom.java.jdeodorant.refactoring.views.CodeSmellVisualizationDataSingleton;
import gr.uom.java.jdeodorant.refactoring.views.ElementChangedListener;
import gr.uom.java.jdeodorant.refactoring.views.MyComboBoxCellEditor;
import gr.uom.java.jdeodorant.refactoring.views.MyRefactoringWizard;
import gr.uom.java.jdeodorant.refactoring.views.SliceAnnotation;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class MicroserviceExtraction extends ViewPart {
	private static final String MESSAGE_DIALOG_TITLE = "Microservice Extraction";
	private TreeViewer treeViewer;
	private Action identifyBadSmellsAction;
	private Action applyRefactoringAction;
	private Action doubleClickAction;
	private Action saveResultsAction;
	private Action packageExplorerAction;
	private ExtractClassCandidateGroup[] candidateRefactoringTable;
	private IJavaProject selectedProject;
	private IJavaProject activeProject;
	private IPackageFragmentRoot selectedPackageFragmentRoot;
	private IPackageFragment selectedPackageFragment;
	private ICompilationUnit selectedCompilationUnit;
	private IType selectedType;
	//
	private String relativePathOfMCFolder;
	private String microserviceName = "microservice";
	// classes of microservice to be extracted
	List<ClassObject> chosenClasses;
	List<ClassObject> monolithClasses;
	// map classesToBeMoved/classesToBeCopied with destination
	// Map<ClassObject, IJavaElement> map = new HashMap<ClassObject,
	// IJavaElement>();
	List<ClassObject> classesToBeMoved = new ArrayList<ClassObject>();
	List<ClassObject> classesToBeCopied = new ArrayList<ClassObject>();
	// Accessibilities that may need changing
	Map<MethodObject, ClassObject> methodsAccessChange = new HashMap<MethodObject, ClassObject>();
	// find relations
	List<EntityObject> entityClasses = new ArrayList<EntityObject>();
	List<AssociationObject> associationObjects = new ArrayList<AssociationObject>();
	List<AssociationObject> associationObjectsToBeBroken;
	// List<Object[]> relations = new ArrayList<Object[]>();
	private Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges = new LinkedHashMap<ICompilationUnit, CompilationUnitChange>();

	class ViewContentProvider implements ITreeContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}

		public void dispose() {
		}

		public Object[] getElements(Object parent) {
			if (candidateRefactoringTable != null) {
				return candidateRefactoringTable;
			} else {
				return new ExtractClassCandidateGroup[] {};
			}
		}

		public Object[] getChildren(Object arg0) {
			if (arg0 instanceof ExtractClassCandidateGroup) {
				return ((ExtractClassCandidateGroup) arg0).getExtractedConcepts().toArray();
			} else if (arg0 instanceof ExtractedConcept) {
				return ((ExtractedConcept) arg0).getConceptClusters().toArray();
			} else {
				return new CandidateRefactoring[] {};
			}
		}

		public Object getParent(Object arg0) {
			if (arg0 instanceof ExtractClassCandidateRefactoring) {
				return getParentConcept((ExtractClassCandidateRefactoring) arg0);
			} else if (arg0 instanceof ExtractedConcept) {
				return getParentCandidateGroup(((ExtractedConcept) arg0).getSourceClass());
			}
			return null;
		}

		public boolean hasChildren(Object arg0) {
			return getChildren(arg0).length > 0;
		}
	}

	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			if (obj instanceof ExtractClassCandidateGroup) {
				ExtractClassCandidateGroup entry = (ExtractClassCandidateGroup) obj;
				switch (index) {
				case 0:
					return "";
				case 1:
					return entry.getSource();
				case 3:
					TreeSet<ExtractClassCandidateRefactoring> set = new TreeSet<ExtractClassCandidateRefactoring>(
							entry.getCandidates());
					return "" + set.first().getDistinctSourceDependencies() + "/"
							+ set.first().getDistinctTargetDependencies();
				default:
					return "";
				}
			} else if (obj instanceof CandidateRefactoring) {
				ExtractClassCandidateRefactoring entry = (ExtractClassCandidateRefactoring) obj;
				switch (index) {
				case 0:
					return "Extract Class";
				case 2:
					return "" + entry.getTopics();
				case 3:
					return "" + entry.getDistinctSourceDependencies() + "/" + entry.getDistinctTargetDependencies();
				case 4:
					Integer userRate = ((ExtractClassCandidateRefactoring) entry).getUserRate();
					return (userRate == null) ? "" : userRate.toString();
				default:
					return "";
				}
			} else if (obj instanceof ExtractedConcept) {
				ExtractedConcept entry = (ExtractedConcept) obj;
				switch (index) {
				case 1:
					return "      " + entry.getTopics();
				default:
					return "";
				}
			} else {
				return "";
			}
		}

		public Image getColumnImage(Object obj, int index) {
			Image image = null;
			if (obj instanceof ExtractClassCandidateRefactoring) {
				int rate = -1;
				Integer userRate = ((ExtractClassCandidateRefactoring) obj).getUserRate();
				if (userRate != null)
					rate = userRate;
				switch (index) {
				case 4:
					if (rate != -1) {
						image = Activator.getImageDescriptor("/icons/" + String.valueOf(rate) + ".jpg").createImage();
					}
				default:
					break;
				}
			}
			return image;
		}

		public Image getImage(Object obj) {
			return null;
		}
	}

	class NameSorter extends ViewerSorter {
		public int compare(Viewer viewer, Object obj1, Object obj2) {
			if (obj1 instanceof CandidateRefactoring && obj2 instanceof CandidateRefactoring) {
				ExtractClassCandidateRefactoring candidate1 = (ExtractClassCandidateRefactoring) obj1;
				ExtractClassCandidateRefactoring candidate2 = (ExtractClassCandidateRefactoring) obj2;
				return candidate1.compareTo(candidate2);
			} else if (obj1 instanceof ExtractedConcept && obj2 instanceof ExtractedConcept) {
				ExtractedConcept concept1 = (ExtractedConcept) obj1;
				ExtractedConcept concept2 = (ExtractedConcept) obj2;
				return concept1.compareTo(concept2);
			} else {
				ExtractClassCandidateGroup group1 = (ExtractClassCandidateGroup) obj1;
				ExtractClassCandidateGroup group2 = (ExtractClassCandidateGroup) obj2;
				return group1.compareTo(group2);
			}
		}
	}

	private ISelectionListener selectionListener = new ISelectionListener() {
		public void selectionChanged(IWorkbenchPart sourcepart, ISelection selection) {
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				Object element = structuredSelection.getFirstElement();
				IJavaProject javaProject = null;
				if (element instanceof IJavaProject) {
					javaProject = (IJavaProject) element;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
					selectedType = null;
				} else if (element instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot packageFragmentRoot = (IPackageFragmentRoot) element;
					javaProject = packageFragmentRoot.getJavaProject();
					selectedPackageFragmentRoot = packageFragmentRoot;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
					selectedType = null;
				} else if (element instanceof IPackageFragment) {
					IPackageFragment packageFragment = (IPackageFragment) element;
					javaProject = packageFragment.getJavaProject();
					selectedPackageFragment = packageFragment;
					selectedPackageFragmentRoot = null;
					selectedCompilationUnit = null;
					selectedType = null;
				} else if (element instanceof ICompilationUnit) {
					ICompilationUnit compilationUnit = (ICompilationUnit) element;
					javaProject = compilationUnit.getJavaProject();
					selectedCompilationUnit = compilationUnit;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedType = null;
				} else if (element instanceof IType) {
					IType type = (IType) element;
					javaProject = type.getJavaProject();
					selectedType = type;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
				}
				if (javaProject != null && !javaProject.equals(selectedProject)) {
					selectedProject = javaProject;
					/*
					 * if(candidateRefactoringTable != null)
					 * tableViewer.remove(candidateRefactoringTable);
					 */
					identifyBadSmellsAction.setEnabled(true);
				}
			}
		}
	};

	/**
	 * The constructor.
	 */
	public MicroserviceExtraction() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		treeViewer = new TreeViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		treeViewer.setContentProvider(new ViewContentProvider());
		treeViewer.setLabelProvider(new ViewLabelProvider());
		treeViewer.setSorter(new NameSorter());
		treeViewer.setInput(getViewSite());
		TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(20, true));
		layout.addColumnData(new ColumnWeightData(40, true));
		layout.addColumnData(new ColumnWeightData(40, true));
		layout.addColumnData(new ColumnWeightData(40, true));
		layout.addColumnData(new ColumnWeightData(20, true));
		treeViewer.getTree().setLayout(layout);
		treeViewer.getTree().setLayoutData(new GridData(GridData.FILL_BOTH));
		new TreeColumn(treeViewer.getTree(), SWT.LEFT).setText("Refactoring Type");
		new TreeColumn(treeViewer.getTree(), SWT.LEFT).setText("Source Class/General Concept");
		new TreeColumn(treeViewer.getTree(), SWT.LEFT).setText("Extractable Concept");
		new TreeColumn(treeViewer.getTree(), SWT.LEFT).setText("Source/Extracted accessed members");
		new TreeColumn(treeViewer.getTree(), SWT.LEFT).setText("Rate it!");

		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				treeViewer.getTree().setMenu(null);
				IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
				if (selection instanceof IStructuredSelection) {
					IStructuredSelection structuredSelection = (IStructuredSelection) selection;
					Object[] selectedItems = structuredSelection.toArray();
					if (selection.getFirstElement() instanceof ExtractClassCandidateRefactoring
							&& selectedItems.length == 1) {
						ExtractClassCandidateRefactoring candidateRefactoring = (ExtractClassCandidateRefactoring) selection
								.getFirstElement();
						treeViewer.getTree().setMenu(getRightClickMenu(treeViewer, candidateRefactoring));
					}
				}
			}
		});

		treeViewer.expandAll();

		for (int i = 0, n = treeViewer.getTree().getColumnCount(); i < n; i++) {
			treeViewer.getTree().getColumn(i).pack();
		}

		treeViewer.setColumnProperties(new String[] { "type", "group", "source", "ep", "rate" });
		treeViewer.setCellEditors(new CellEditor[] { new TextCellEditor(), new TextCellEditor(), new TextCellEditor(),
				new TextCellEditor(), new MyComboBoxCellEditor(treeViewer.getTree(),
						new String[] { "0", "1", "2", "3", "4", "5" }, SWT.READ_ONLY) });

		treeViewer.setCellModifier(new ICellModifier() {
			public boolean canModify(Object element, String property) {
				return property.equals("rate");
			}

			public Object getValue(Object element, String property) {
				if (element instanceof ExtractClassCandidateRefactoring) {
					ExtractClassCandidateRefactoring candidate = (ExtractClassCandidateRefactoring) element;
					if (candidate.getUserRate() != null)
						return candidate.getUserRate();
					else
						return 0;
				}
				return 0;
			}

			public void modify(Object element, String property, Object value) {
				TreeItem item = (TreeItem) element;
				Object data = item.getData();
				if (data instanceof ExtractClassCandidateRefactoring) {
					ExtractClassCandidateRefactoring candidate = (ExtractClassCandidateRefactoring) data;
					candidate.setUserRate((Integer) value);
					IPreferenceStore store = Activator.getDefault().getPreferenceStore();
					boolean allowUsageReporting = store.getBoolean(PreferenceConstants.P_ENABLE_USAGE_REPORTING);
					if (allowUsageReporting) {
						Tree tree = treeViewer.getTree();
						int groupPosition = -1;
						int totalGroups = tree.getItemCount();
						int totalOpportunities = 0;
						for (int i = 0; i < tree.getItemCount(); i++) {
							TreeItem treeItem = tree.getItem(i);
							ExtractClassCandidateGroup group = (ExtractClassCandidateGroup) treeItem.getData();
							if (group.getSource().equals(candidate.getSource())) {
								groupPosition = i;
							}
							totalOpportunities += group.getCandidates().size();
						}
						try {
							Set<VariableDeclaration> extractedFieldFragments = candidate.getExtractedFieldFragments();
							Set<MethodDeclaration> extractedMethods = candidate.getExtractedMethods();
							boolean allowSourceCodeReporting = store
									.getBoolean(PreferenceConstants.P_ENABLE_SOURCE_CODE_REPORTING);
							String declaringClass = candidate.getSourceClassTypeDeclaration().resolveBinding()
									.getQualifiedName();
							String content = URLEncoder.encode("project_name", "UTF-8") + "="
									+ URLEncoder.encode(activeProject.getElementName(), "UTF-8");
							content += "&" + URLEncoder.encode("source_class_name", "UTF-8") + "="
									+ URLEncoder.encode(declaringClass, "UTF-8");
							String extractedElementsSourceCode = "";
							String extractedFieldsText = "";
							for (VariableDeclaration fieldFragment : extractedFieldFragments) {
								extractedFieldsText += fieldFragment.resolveBinding().toString() + "\n";
								extractedElementsSourceCode += fieldFragment.resolveBinding().toString() + "\n";
							}
							content += "&" + URLEncoder.encode("extracted_fields", "UTF-8") + "="
									+ URLEncoder.encode(extractedFieldsText, "UTF-8");
							String extractedMethodsText = "";
							for (MethodDeclaration method : extractedMethods) {
								extractedMethodsText += method.resolveBinding().toString() + "\n";
								extractedElementsSourceCode += method.toString() + "\n";
							}
							content += "&" + URLEncoder.encode("extracted_methods", "UTF-8") + "="
									+ URLEncoder.encode(extractedMethodsText, "UTF-8");
							content += "&" + URLEncoder.encode("group_position", "UTF-8") + "="
									+ URLEncoder.encode(String.valueOf(groupPosition), "UTF-8");
							content += "&" + URLEncoder.encode("total_groups", "UTF-8") + "="
									+ URLEncoder.encode(String.valueOf(totalGroups), "UTF-8");
							content += "&" + URLEncoder.encode("total_opportunities", "UTF-8") + "="
									+ URLEncoder.encode(String.valueOf(totalOpportunities), "UTF-8");
							content += "&" + URLEncoder.encode("EP", "UTF-8") + "="
									+ URLEncoder.encode(String.valueOf(0.0), "UTF-8");
							if (allowSourceCodeReporting)
								content += "&" + URLEncoder.encode("extracted_elements_source_code", "UTF-8") + "="
										+ URLEncoder.encode(extractedElementsSourceCode, "UTF-8");
							content += "&" + URLEncoder.encode("rating", "UTF-8") + "="
									+ URLEncoder.encode(String.valueOf(candidate.getUserRate()), "UTF-8");
							content += "&" + URLEncoder.encode("username", "UTF-8") + "="
									+ URLEncoder.encode(System.getProperty("user.name"), "UTF-8");
							content += "&" + URLEncoder.encode("tb", "UTF-8") + "=" + URLEncoder.encode("3", "UTF-8");
							URL url = new URL(Activator.RANK_URL);
							URLConnection urlConn = url.openConnection();
							urlConn.setDoInput(true);
							urlConn.setDoOutput(true);
							urlConn.setUseCaches(false);
							urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
							DataOutputStream printout = new DataOutputStream(urlConn.getOutputStream());
							printout.writeBytes(content);
							printout.flush();
							printout.close();
							DataInputStream input = new DataInputStream(urlConn.getInputStream());
							input.close();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
					treeViewer.update(data, null);
				}
			}
		});

		treeViewer.getTree().setLinesVisible(true);
		treeViewer.getTree().setHeaderVisible(true);
		makeActions();
		hookDoubleClickAction();
		contributeToActionBars();
		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(selectionListener);
		JavaCore.addElementChangedListener(ElementChangedListener.getInstance());
		getSite().getWorkbenchWindow().getWorkbench().getOperationSupport().getOperationHistory()
				.addOperationHistoryListener(new IOperationHistoryListener() {
					public void historyNotification(OperationHistoryEvent event) {
						int eventType = event.getEventType();
						if (eventType == OperationHistoryEvent.UNDONE || eventType == OperationHistoryEvent.REDONE
								|| eventType == OperationHistoryEvent.OPERATION_ADDED
								|| eventType == OperationHistoryEvent.OPERATION_REMOVED) {
							if (activeProject != null && CompilationUnitCache.getInstance().getAffectedProjects()
									.contains(activeProject)) {
								applyRefactoringAction.setEnabled(false);
								saveResultsAction.setEnabled(false);
								packageExplorerAction.setEnabled(false);
							}
						}
					}
				});
	}

	private Menu getRightClickMenu(TreeViewer treeViewer, final ExtractClassCandidateRefactoring candidateRefactoring) {
		Menu popupMenu = new Menu(treeViewer.getControl());
		MenuItem textualDiffMenuItem = new MenuItem(popupMenu, SWT.NONE);
		textualDiffMenuItem.setText("Visualize Code Smell");
		textualDiffMenuItem.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent arg) {
				CodeSmellVisualizationDataSingleton.setData(candidateRefactoring.getGodClassVisualizationData());
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart viewPart = page.findView(CodeSmellVisualization.ID);
				if (viewPart != null)
					page.hideView(viewPart);
				try {
					page.showView(CodeSmellVisualization.ID);
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}

			public void widgetDefaultSelected(SelectionEvent arg) {
			}
		});
		popupMenu.setVisible(false);
		return popupMenu;
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(identifyBadSmellsAction);
		manager.add(applyRefactoringAction);
		manager.add(saveResultsAction);
		manager.add(packageExplorerAction);
	}

	private void makeActions() {
		identifyBadSmellsAction = new Action() {
			public void run() {
				boolean wasAlreadyOpen = false;
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart viewPart = page.findView(CodeSmellPackageExplorer.ID);
				if (viewPart != null) {
					page.hideView(viewPart);
					wasAlreadyOpen = true;
				}
				activeProject = selectedProject;
				CompilationUnitCache.getInstance().clearCache();
				candidateRefactoringTable = getTable();
				treeViewer.setContentProvider(new ViewContentProvider());
				// XXX: here we enable the move class refactoring action
				applyRefactoringAction.setEnabled(true);
				saveResultsAction.setEnabled(true);
				packageExplorerAction.setEnabled(true);
				if (wasAlreadyOpen)
					openPackageExplorerViewPart();
			}
		};
		identifyBadSmellsAction.setToolTipText("Identify Bad Smells");
		identifyBadSmellsAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		identifyBadSmellsAction.setEnabled(false);

		saveResultsAction = new Action() {
			public void run() {
				saveResults();
			}
		};
		saveResultsAction.setToolTipText("Save Results");
		saveResultsAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ETOOL_SAVE_EDIT));
		saveResultsAction.setEnabled(false);

		packageExplorerAction = new Action() {
			public void run() {
				// open the Code Smell Package Explorer only if it is closed
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart viewPart = page.findView(CodeSmellPackageExplorer.ID);
				if (viewPart == null/*
									 * || !CodeSmellPackageExplorer.CODE_SMELL_TYPE.equals(CodeSmellType.GOD_CLASS)
									 */)
					openPackageExplorerViewPart();
			}
		};
		packageExplorerAction.setToolTipText("Code Smell Package Explorer");
		packageExplorerAction.setImageDescriptor(Activator.getImageDescriptor("/icons/" + "compass.png"));
		packageExplorerAction.setEnabled(false);

		// Implements the move class refactoring to the target package
		applyRefactoringAction = new Action() {
			public void run() {
				try {
					// Add public access modifier to methods whose class will be extracted to
					// microservice
					/*
					 * for(MethodObject methodObject:methodsAccessChange.keySet()) {
					 * addPublicAccesModifier(methodObject); }
					 */
					// Creating the microservice package
					ICompilationUnit chosenCl = (ICompilationUnit) classesToBeMoved.get(0).getITypeRoot()
							.getPrimaryElement();
					IFolder McFolder = classesToBeMoved.get(0).getITypeRoot().getCorrespondingResource().getProject()
							.getFolder(relativePathOfMCFolder + "/" + microserviceName);
					// IFolder McFolder =
					// chosenCl.getJavaProject().getPackageFragments()[2].getCorrespondingResource().getProject().getFolder("/src/main/java/com/mgiandia/Microservice");
					if (!McFolder.exists()) {
						McFolder.create(true, true, null);
					}
					// Copying classes to be copied
					for (int j = 0; j < classesToBeCopied.size(); j++) {
						copyClass(classesToBeCopied.get(j));
					}
					// Update the imports of classes to be moved to the new copied classes
					for (ClassObject toMove : classesToBeMoved) {
						for (ClassObject toCopy : classesToBeCopied) {
							updateImportsForClassesToBeMovedToTheCopied(toMove, toCopy);
						}
					}
					// Grouping classes to be moved per package
					List<String> destinationNames = new ArrayList<String>();
					Map<ICompilationUnit, String> classMap = new HashMap<ICompilationUnit, String>();
					for (ClassObject ob : classesToBeMoved) {
						ICompilationUnit cuTemp = (ICompilationUnit) ob.getITypeRoot().getPrimaryElement();
						String location = cuTemp.getCorrespondingResource().getParent().getName();
						if (ob.getName().contains("Service")) {
							location = "service";
						}
						classMap.put(cuTemp, location);
						if (!destinationNames.contains(location)) {
							destinationNames.add(location);
						}
					}
					// Moving classes per package
					for (String s : destinationNames) {
						List<ICompilationUnit> cus = new ArrayList<ICompilationUnit>();
						for (ICompilationUnit c : classMap.keySet()) {
							if (classMap.get(c).equals(s)) {
								cus.add(c);
							}
						}
						RefactoringContribution contribution = RefactoringCore
								.getRefactoringContribution(IJavaRefactorings.MOVE);
						IFolder processFolder = cus.get(0).getJavaProject().getCorrespondingResource().getProject()
								.getFolder(relativePathOfMCFolder + "/" + microserviceName + "/" + s);
						if (!processFolder.exists()) {
							processFolder.create(true, true, null);
						}
						MoveDescriptor descriptor = (MoveDescriptor) contribution.createDescriptor();
						IJavaElement pack = JavaCore.create(processFolder);
						ICompilationUnit[] moved = new ICompilationUnit[cus.size()];
						moved = cus.toArray(moved);
						RefactoringStatus status = new RefactoringStatus();
						MoveClassRefactoring moveclassRefactoring = new MoveClassRefactoring(descriptor, moved,
								cus.get(0).getResource().getProject().getName(), pack, true, status);
						Refactoring refactoring = moveclassRefactoring.getRefactoring();
						// IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
						JavaUI.openInEditor(classesToBeCopied.get(0).getITypeRoot().getPrimaryElement());
						IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
						page.closeAllEditors(true);
						MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, applyRefactoringAction);
						RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
						String titleForFailedChecks = ""; //$NON-NLS-1$
						op.run(getSite().getShell(), titleForFailedChecks);

					}
				} catch (PartInitException e) {
					e.printStackTrace();
				} catch (JavaModelException e) {
					e.printStackTrace();
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (MalformedTreeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// }
				// }
			}
		};
		applyRefactoringAction.setToolTipText("Apply Refactoring");
		applyRefactoringAction.setImageDescriptor(
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_DEF_VIEW));
		// FIXME: the action is not enabled by default (is it conditionally enabled?)
		applyRefactoringAction.setEnabled(false);

		doubleClickAction = new Action() {
			public void run() {
				IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
				if (selection.getFirstElement() instanceof CandidateRefactoring) {
					CandidateRefactoring candidate = (CandidateRefactoring) selection.getFirstElement();
					if (candidate.getSourceClassTypeDeclaration() != null) {
						IFile sourceFile = candidate.getSourceIFile();
						try {
							IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
							ITextEditor sourceEditor = (ITextEditor) JavaUI.openInEditor(sourceJavaElement);
							List<Position> positions = candidate.getPositions();
							AnnotationModel annotationModel = (AnnotationModel) sourceEditor.getDocumentProvider()
									.getAnnotationModel(sourceEditor.getEditorInput());
							Iterator<Annotation> annotationIterator = annotationModel.getAnnotationIterator();
							while (annotationIterator.hasNext()) {
								Annotation currentAnnotation = annotationIterator.next();
								if (currentAnnotation.getType().equals(SliceAnnotation.EXTRACTION)) {
									annotationModel.removeAnnotation(currentAnnotation);
								}
							}
							Position firstPosition = null;
							Position lastPosition = null;
							int minOffset = Integer.MAX_VALUE;
							int maxOffset = -1;
							for (Position position : positions) {
								SliceAnnotation annotation = new SliceAnnotation(SliceAnnotation.EXTRACTION,
										candidate.getAnnotationText());
								annotationModel.addAnnotation(annotation, position);
								if (position.getOffset() < minOffset) {
									minOffset = position.getOffset();
									firstPosition = position;
								}
								if (position.getOffset() > maxOffset) {
									maxOffset = position.getOffset();
									lastPosition = position;
								}
							}
							int offset = firstPosition.getOffset();
							int length = lastPosition.getOffset() + lastPosition.getLength()
									- firstPosition.getOffset();
							sourceEditor.setHighlightRange(offset, length, true);
						} catch (PartInitException e) {
							e.printStackTrace();
						} catch (JavaModelException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};
	}

	private void hookDoubleClickAction() {
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		treeViewer.getControl().setFocus();
	}

	public void dispose() {
		super.dispose();
		getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionListener);
	}

	private ExtractClassCandidateGroup[] getTable() {
		ExtractClassCandidateGroup[] table = null;
		try {
			IWorkbench wb = PlatformUI.getWorkbench();
			IProgressService ps = wb.getProgressService();
			if (ASTReader.getSystemObject() != null && activeProject.equals(ASTReader.getExaminedProject())) {
				new ASTReader(activeProject, ASTReader.getSystemObject(), null);
			} else {
				ps.busyCursorWhile(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						try {
							new ASTReader(activeProject, monitor);
						} catch (CompilationErrorDetectedException e) {
							Display.getDefault().asyncExec(new Runnable() {
								public void run() {
									MessageDialog.openInformation(
											PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
											MESSAGE_DIALOG_TITLE,
											"Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
								}
							});
						}
					}
				});
			}
			SystemObject systemObject = ASTReader.getSystemObject();
			RefactoringContext.getInstance().initialize(activeProject);
			JpaModel jpaModel = new JpaModel();
			jpaModel.initialize(systemObject);
			entityClasses = new ArrayList<EntityObject>(jpaModel.getEntities());

			if (systemObject != null) {
				Set<ClassObject> classObjectsToBeExamined = new LinkedHashSet<ClassObject>();
				if (selectedPackageFragmentRoot != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedPackageFragmentRoot));
				} else if (selectedPackageFragment != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedPackageFragment));
				} else if (selectedCompilationUnit != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedCompilationUnit));
				} else if (selectedType != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedType));
				} else {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects());
				}
				List<ClassObject> classes = new ArrayList<ClassObject>();
				classes.addAll(systemObject.getClassObjects());

				Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				
				
				// show file selector for destination root package
				IResource destinationPackageRoot = selectDestinationPackageRoot(shell);
				
				if (destinationPackageRoot != null) {
					relativePathOfMCFolder = destinationPackageRoot.getProjectRelativePath().toString();
					System.out.println(destinationPackageRoot.getProjectRelativePath());
				}

				// show input for destination package name
				shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				microserviceName = inputDestinationPackageName(shell);

				List<IPackageFragment> rootPackages = RefactoringContext.getInstance().getRootPackages();
				// select domain classes to be extracted
				// FIXME: should be characterized by JPA annotations
				chosenClasses = new ArrayList<ClassObject>();
				CheckedTreeSelectionDialog dialogPickClasses = new CheckedTreeSelectionDialog(shell,
						new JavaElementLabelProvider(), new DomainClassesContentProvider());
				dialogPickClasses.setInput(RefactoringContext.getInstance().getTargetProject());

				//dialogPickClasses.setInitialSelections(rootPackages.toArray());
				dialogPickClasses.setTitle("Select Domain classes for Microservice extraction");
				dialogPickClasses
						.setMessage("Select the Domain class/classes you want to extract into the Microservice:");
				dialogPickClasses.open();
				
				Object[] resultClasses = dialogPickClasses.getResult();
				if (resultClasses != null) {
					for (Object obj : resultClasses) {
						if (obj instanceof IJavaElement) {
							System.out.println(((IJavaElement) obj).getPrimaryElement().getElementName());
							ICompilationUnit cu = (ICompilationUnit) ((IJavaElement) obj).getPrimaryElement();
							Set<ClassObject> classObs = systemObject.getClassObjects(cu);
							for (ClassObject classOb : classObs) {
								System.out.println(classOb.getName() + "    " + classes.contains(classOb));
								chosenClasses.add(classOb);
							}
						}
					}
				}

				// TEMPORARY
				monolithClasses = new ArrayList<ClassObject>();
				for (final ClassObject classOb : classes) {
					if ((classOb.getName().equals("com.mgiandia.library.domain.Book"))
							|| (classOb.getName().equals("com.mgiandia.library.domain.Author"))
							|| (classOb.getName().equals("com.mgiandia.library.domain.Publisher"))) {
						// chosenClasses.add(classOb);
					} else if ((classOb.getITypeRoot().getParent().getElementName()
							.equals(selectedType.getTypeRoot().getParent().getElementName()))
							&& (!classOb.containsMethodWithTestAnnotation()) && (!chosenClasses.contains(classOb))) {
						monolithClasses.add(classOb);
					}
				}
				for (final ClassObject classOb : classes) {

					// Adding Methods and their class with none as access and are used by a class
					// that is not in the chosen classes to be extracted
					if (!classOb.isTestClass() && (!chosenClasses.contains(classOb))) {
						for (MethodObject method : classOb.getMethodList()) {
							for (MethodInvocationObject methodInvocation : method.getMethodInvocations()) {
								for (ClassObject c3 : chosenClasses) {
									if (methodInvocation.getOriginClassName().equals(c3.getName())) {
										for (MethodObject m : c3.getMethodList()) {
											if ((m.getName().equals(methodInvocation.getMethodName()))
													&& (m.getAccess().equals(Access.NONE))) {
												System.out.println(classOb.getName() + "          " + method.getName()
														+ "     " + methodInvocation + "       "
														+ methodInvocation.getOriginClassName());
												methodsAccessChange.put(m, c3);
											}
										}
									}
								}
							}
						}
					}
					
					System.out.println("-------------------------");

					for (ClassObject cl : chosenClasses) {
						// Classes that need to be copied
						if ((!chosenClasses.contains(classOb)) && (cl.hasFieldType(classOb.getName()))
								&& (!JpaModel.isEntity(classOb)) && (!classOb.isTestClass())) {
							// System.out.println("We need to copy "+classOb.getName());
							classesToBeCopied.add(classOb);
						}
						// Classes that need to be moved
						if (classesToBeCopied.contains(classOb)) {
							boolean hasDependency = false;
							for (ClassObject monolithClass : monolithClasses) {
								if (monolithClass.hasFieldType(classOb.getName())) {
									hasDependency = true;
								}
							}
							if (!hasDependency) {
								classesToBeCopied.remove(classOb);
								classesToBeMoved.add(classOb);
							}

						}
					}
				}
				
				List<ClassObject> ExtraclassesToBeCopied = new ArrayList<ClassObject>();
				for (ClassObject cl2 : classesToBeCopied) {
					for (ClassObject classOb : classes) {
						if (cl2.hasFieldType(classOb.getName()) && (!classesToBeCopied.contains(classOb))) {
							System.out.println(classOb.getName());
							ExtraclassesToBeCopied.add(classOb);
						}
					}
				}
				for (ClassObject ob : ExtraclassesToBeCopied) {
					classesToBeCopied.add(ob);
				}
				for (ClassObject obj : chosenClasses) {
					classesToBeMoved.add(obj);
				}
				for (ClassObject obj : classesToBeMoved) {
					if (monolithClasses.contains(obj)) {
						monolithClasses.remove(obj);
					}
				}
				// Creating AssociationObjects
				associationObjects = new ArrayList<AssociationObject>();
				for (EntityObject entityObject1 : entityClasses) {
					for (FieldObject fieldObject : entityObject1.getAssociatedObjects()) {
						for (EntityObject entityObject2 : entityClasses) {
							if (fieldObject.getType().getClassType().equals(entityObject2.getClassObject().getName())) {
								if (!checkIfAssociationExists(entityObject1, entityObject2)) {
									AssociationObject associationObject = createAssociationObject(entityObject1,
											entityObject2);
									associationObjects.add(associationObject);
									// System.out.println(entityObject1.getClassObject().getName()+"
									// "+entityObject2.getClassObject().getName()+" "+associationObject.getType());
								}
							} else if (fieldObject.getType().getGenericType() != null) {
								if (fieldObject.getType().getGenericType()
										.equals("<" + entityObject2.getClassObject().getName() + ">")) {
									if (!checkIfAssociationExists(entityObject1, entityObject2)) {
										AssociationObject associationObject = createAssociationObject(entityObject1,
												entityObject2);
										associationObjects.add(associationObject);
										// System.out.println(entityObject1.getClassObject().getName()+"
										// "+entityObject2.getClassObject().getName()+" "+associationObject.getType());
									}
								}
							}
						}
					}
				}
				// AssociationObjects that need to be broken
				associationObjectsToBeBroken = new ArrayList<AssociationObject>();
				for (AssociationObject association : associationObjects) {
					ClassObject classObject1 = association.getOwnerClass().getClassObject();
					ClassObject classObject2 = association.getOwnedClass().getClassObject();
					if ((classesToBeMoved.contains(classObject1) && !classesToBeMoved.contains(classObject2))
							|| ((classesToBeMoved.contains(classObject2)
									&& !classesToBeMoved.contains(classObject1)))) {
						associationObjectsToBeBroken.add(association);
						System.out.println(association.getOwnerClass().getClassObject().getName() + "     "
								+ association.getOwnedClass().getClassObject().getName() + "    "
								+ association.isBidirectional());
					}
					// System.out.println(association.getOwnerClass().getClassObject().getName()+"
					// "+association.getOwnedClass().getClassObject().getName()+"
					// "+association.isBidirectional());
				}
				final AssociationObject association = associationObjectsToBeBroken.get(0);
				BreakAssociationRefactoring breakAssociationRefactoring = new BreakAssociationRefactoring(association,
						classes);
				IJavaProject project = breakAssociationRefactoring.apply();

				new ASTReader(project, ASTReader.getSystemObject(), null);
				systemObject = ASTReader.getSystemObject();
				List<ClassObject> newClasses = new ArrayList<ClassObject>();
				newClasses.addAll(systemObject.getClassObjects());

				System.out.println(newClasses.size() == classes.size());
				boolean flag = true;
				int i = 0;
				while (flag && i < 2) {
					flag = AddRestOfClassesToBeMovedAndCopied(newClasses);
					i++;
				}

				System.out.println("END");
				for (ClassObject obj : classesToBeMoved) {
					System.out.println("MOVE   " + obj.getName());
				}
				for (ClassObject obj : classesToBeCopied) {
					System.out.println("COPY   " + obj.getName());
				}
				
				/** show results in table */

				final Set<String> classNamesToBeExamined = new LinkedHashSet<String>();
				for (ClassObject classObject : classObjectsToBeExamined) {
					if (!classObject.isEnum() && !classObject.isInterface()
							&& !classObject.isGeneratedByParserGenenator())
						classNamesToBeExamined.add(classObject.getName());
				}
				MySystem system = new MySystem(systemObject, true);
				final DistanceMatrix distanceMatrix = new DistanceMatrix(system);
				final List<ExtractClassCandidateRefactoring> extractClassCandidateList = new ArrayList<ExtractClassCandidateRefactoring>();

				/*
				 * ps.busyCursorWhile(new IRunnableWithProgress() { public void
				 * run(IProgressMonitor monitor) throws InvocationTargetException,
				 * InterruptedException { extractClassCandidateList.addAll(distanceMatrix.
				 * getExtractClassCandidateRefactorings(classNamesToBeExamined, monitor)); } });
				 */
				HashMap<String, ExtractClassCandidateGroup> groupedBySourceClassMap = new HashMap<String, ExtractClassCandidateGroup>();
				for (ExtractClassCandidateRefactoring candidate : extractClassCandidateList) {
					if (groupedBySourceClassMap.keySet().contains(candidate.getSourceEntity())) {
						groupedBySourceClassMap.get(candidate.getSourceEntity()).addCandidate(candidate);
					} else {
						ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(candidate.getSourceEntity());
						group.addCandidate(candidate);
						groupedBySourceClassMap.put(candidate.getSourceEntity(), group);
					}
				}
				for (String sourceClass : groupedBySourceClassMap.keySet()) {
					groupedBySourceClassMap.get(sourceClass).groupConcepts();
				}

				table = new ExtractClassCandidateGroup[groupedBySourceClassMap.values().size()];
				int counter = 0;
				for (ExtractClassCandidateGroup candidate : groupedBySourceClassMap.values()) {
					table[counter] = candidate;
					counter++;
				}
			}
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (CompilationErrorDetectedException e) {
			MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					MESSAGE_DIALOG_TITLE,
					"Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
			/*
			 * } catch (JavaModelException e1) { // TODO Auto-generated catch block
			 * e1.printStackTrace();
			 */
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			/*
			 * } catch (CoreException e) { // TODO Auto-generated catch block
			 * e.printStackTrace(); } catch (BadLocationException e) { // TODO
			 * Auto-generated catch block e.printStackTrace();
			 */
		}
		return table;
	}

	private IResource selectDestinationPackageRoot(Shell shell) {
		RefactoringContext refactoringContext = RefactoringContext.getInstance();
		
		// FIXME: would not work for non maven projects
		IContainer mvnSourceFolder = refactoringContext.getMavenSourceFolder();
		//assert mvnSourceFolder.exists();
		
		
		// FIXME: for some reason, initial root works only for source folder, not for packages
		ContainerSelectionDialog dialog = new ContainerSelectionDialog(shell,
				mvnSourceFolder, false,
				"Select the folder that the microservice will be located");
		
		//dialog.setInitialSelections(rootPackage);
		dialog.showClosedProjects(false);
		dialog.open();

		Object[] result = dialog.getResult();
		if (result == null || result.length == 0) {
			return null;
		}
		
		IPath path = (IPath) result[0];
		// remove the project name from the path
		path = path.removeFirstSegments(1);
		IResource destinationPkgResource = refactoringContext.getProjectContainer().findMember(path);
		return destinationPkgResource;
	}

	/**
	 * Receive from user input the destination package for extracted microservice
	 * 
	 * @param shell
	 */
	private String inputDestinationPackageName(Shell shell) {
		InputDialog inputDialog = new InputDialog(shell, "Input Dialog", "Enter the name of the microservice folder:",
				"", null);
		int input = inputDialog.open();
		if (input == Window.OK) {
			String name = inputDialog.getValue();
			System.out.println("The name chosen is: " + name);
			return name;
			
		}
		return null;
	}

	private ExtractClassCandidateGroup getParentCandidateGroup(String sourceClass) {
		String[] classes = new String[candidateRefactoringTable.length];
		for (int i = 0; i < candidateRefactoringTable.length; i++) {
			classes[i] = candidateRefactoringTable[i].getSource();
		}
		for (int i = 0; i < classes.length; i++) {
			if (classes[i].equals(sourceClass)) {
				return candidateRefactoringTable[i];
			}
		}
		return null;
	}

	private ExtractedConcept getParentConcept(ExtractClassCandidateRefactoring candidate) {
		for (int i = 0; i < candidateRefactoringTable.length; i++) {
			for (ExtractedConcept concept : candidateRefactoringTable[i].getExtractedConcepts()) {
				HashSet<Entity> copiedConceptEntities = new HashSet<Entity>(concept.getConceptEntities());
				copiedConceptEntities.retainAll(candidate.getExtractedEntities());
				if (!copiedConceptEntities.isEmpty()) {
					return concept;
				}
			}
		}
		return null;
	}

	private void saveResults() {
		FileDialog fd = new FileDialog(getSite().getWorkbenchWindow().getShell(), SWT.SAVE);
		fd.setText("Save Results");
		String[] filterExt = { "*.txt" };
		fd.setFilterExtensions(filterExt);
		String selected = fd.open();
		if (selected != null) {
			try {
				BufferedWriter out = new BufferedWriter(new FileWriter(selected));
				Tree tree = treeViewer.getTree();
				/*
				 * TreeColumn[] columns = tree.getColumns(); for(int i=0; i<columns.length; i++)
				 * { if(i == columns.length-1) out.write(columns[i].getText()); else
				 * out.write(columns[i].getText() + "\t"); } out.newLine();
				 */
				for (int i = 0; i < tree.getItemCount(); i++) {
					TreeItem treeItem = tree.getItem(i);
					ExtractClassCandidateGroup group = (ExtractClassCandidateGroup) treeItem.getData();
					for (CandidateRefactoring candidate : group.getCandidates()) {
						out.write(candidate.toString());
						out.newLine();
					}
				}
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void openPackageExplorerViewPart() {
		try {
			ArrayList<CandidateRefactoring> candidates = new ArrayList<CandidateRefactoring>();
			for (ExtractClassCandidateGroup group : candidateRefactoringTable) {
				ArrayList<ExtractClassCandidateRefactoring> extractCandidates = group.getCandidates();
				candidates.addAll(extractCandidates);
			}
			CodeSmellVisualizationDataSingleton.setCandidates(
					(CandidateRefactoring[]) candidates.toArray(new CandidateRefactoring[candidates.size()]));
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IViewPart viewPart = page.findView(CodeSmellPackageExplorer.ID);
			CodeSmellPackageExplorer.CODE_SMELL_TYPE = CodeSmellType.MICROSERVICE_EXTRACTION;
			if (viewPart != null)
				page.hideView(viewPart);
			page.showView(CodeSmellPackageExplorer.ID);

		} catch (PartInitException e) {
			e.printStackTrace();
		}
	}

	public void setSelectedLine(CandidateRefactoring candidateRefactoring) {
		Tree tree = treeViewer.getTree();
		for (int i = 0; i < tree.getItemCount(); i++) {
			TreeItem treeItem = tree.getItem(i);
			ExtractClassCandidateGroup group = (ExtractClassCandidateGroup) treeItem.getData();
			if (group.getCandidates().contains(candidateRefactoring)) {
				treeItem.setExpanded(true);
				treeViewer.refresh();
				setSelectedLineWithinCandidateGroup(tree, treeItem, candidateRefactoring);
				break;
			}
		}
	}

	private void setSelectedLineWithinCandidateGroup(Tree tree, TreeItem candidateGroupTreeItem,
			CandidateRefactoring candidateRefactoring) {
		for (int i = 0; i < candidateGroupTreeItem.getItemCount(); i++) {
			TreeItem conceptTreeItem = candidateGroupTreeItem.getItem(i);
			ExtractedConcept concept = (ExtractedConcept) conceptTreeItem.getData();
			if (concept.getConceptClusters().contains(candidateRefactoring)) {
				conceptTreeItem.setExpanded(true);
				treeViewer.refresh();
				for (int j = 0; j < conceptTreeItem.getItemCount(); j++) {
					TreeItem candidateTreeItem = conceptTreeItem.getItem(j);
					CandidateRefactoring candidate = (CandidateRefactoring) candidateTreeItem.getData();
					if (candidate.equals(candidateRefactoring)) {
						tree.setSelection(candidateTreeItem);
						treeViewer.refresh();
						break;
					}
				}
				break;
			}
		}
	}

	public void addPublicAccesModifier(MethodObject methodObject) {
		ICompilationUnit cu = (ICompilationUnit) methodsAccessChange.get(methodObject).getITypeRoot()
				.getPrimaryElement();
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		astRoot.recordModifications();
		System.out.println(methodObject.getName());
		System.out.println(astRoot.findDeclaringNode(methodObject.getMethodDeclaration().resolveBinding().getKey()));
		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		ListRewrite listRewrite = rewriter.getListRewrite(
				astRoot.findDeclaringNode(methodObject.getMethodDeclaration().resolveBinding().getKey()),
				MethodDeclaration.MODIFIERS2_PROPERTY);
		Modifier publicModifier = astRoot.getAST().newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
		listRewrite.insertFirst(publicModifier, null);
		TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			Document document = new Document(cu.getSource());
			edits.apply(document);
			cu.getBuffer().setContents(document.get());
			cu.save(null, true);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void copyClass(ClassObject classObject) {
		ICompilationUnit cuCopy = (ICompilationUnit) classObject.getITypeRoot().getPrimaryElement();
		IFolder processFolder2;
		try {
			processFolder2 = classObject.getITypeRoot().getCorrespondingResource().getProject()
					.getFolder(relativePathOfMCFolder + "/" + microserviceName + "/"
							+ cuCopy.getCorrespondingResource().getParent().getName());
			if (!processFolder2.exists()) {
				processFolder2.create(true, true, null);
			}
			IJavaElement parent = JavaCore.create(processFolder2);
			cuCopy.copy(parent, cuCopy, cuCopy.getElementName(), true, null);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void updateImportsForClassesToBeMovedToTheCopied(ClassObject toMove, ClassObject toCopy) {
		ICompilationUnit cu = (ICompilationUnit) toMove.getITypeRoot().getPrimaryElement();
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		astRoot.recordModifications();

		String[] arr = toCopy.getName().split("\\.");
		String[] folderNameArray = relativePathOfMCFolder.split("/");
		String folderName = folderNameArray[folderNameArray.length - 1];
		for (int i = 0; i < arr.length - 1; i++) {
			if (arr[i].equals(folderName)) {
				arr[i + 1] = microserviceName;
				break;
			}
		}
		// System.out.println(astRoot.imports());
		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		try {
			if (toMove.getITypeRoot().getParent().equals(toCopy.getITypeRoot().getParent())
					&& (toMove.hasFieldType(toCopy.getName()) || toCopy.hasFieldType(toMove.getName()))) {
				ListRewrite listRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
				ImportDeclaration newImport = astRoot.getAST().newImportDeclaration();

				newImport.setName(astRoot.getAST().newName(arr));
				listRewrite.insertFirst(newImport, null);
				ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
				TextEdit importEdits;
				importEdits = importRewrite.rewriteImports(null);
				TextEdit edits = rewriter.rewriteAST();
				edits.addChild(importEdits);
				Document document = new Document(cu.getSource());
				edits.apply(document);
				cu.getBuffer().setContents(document.get());
				cu.save(null, true);
				// cu.commitWorkingCopy(true, null);
			}
			for (Object o : astRoot.imports()) {
				ImportDeclaration importDeclaration = (ImportDeclaration) o;
				// System.out.println(importDeclaration.getName().getFullyQualifiedName()+"
				// "+toCopy.getName());
				if (importDeclaration.getName().getFullyQualifiedName().equals(toCopy.getName())) {
					System.out.println(toMove.getName() + "  " + toCopy.getName());
					ListRewrite listRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
					// System.out.println("hello "+arr.length);
					ImportDeclaration id = astRoot.getAST().newImportDeclaration();
					id.setName(astRoot.getAST().newName(arr));
					listRewrite.replace(importDeclaration, id, null);
					ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
					TextEdit importEdits = importRewrite.rewriteImports(null);
					TextEdit edits = rewriter.rewriteAST();
					edits.addChild(importEdits);
					Document document = new Document(cu.getSource());
					edits.apply(document);
					cu.getBuffer().setContents(document.get());
					cu.save(null, true);
					// cu.commitWorkingCopy(true, null);
				}
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean checkIfAssociationExists(EntityObject entityObject1, EntityObject entityObject2) {
		boolean associationAlreadyExists = false;
		for (AssociationObject association : associationObjects) {
			if ((association.getOwnerClass().equals(entityObject1) && association.getOwnedClass().equals(entityObject2))
					|| (association.getOwnerClass().equals(entityObject2)
							&& association.getOwnedClass().equals(entityObject1))) {
				associationAlreadyExists = true;
				association.setBidirectional(true);
			}
		}
		return associationAlreadyExists;
	}

	public AssociationObject createAssociationObject(EntityObject entityObject1, EntityObject entityObject2) {
		FieldObject fieldObject = entityObject1.getAssociatedObjectByClass(entityObject2.getClassObject());
		org.eclipse.jdt.core.dom.Annotation annotation = entityObject1
				.getAssociatedObjectAnnotationByField(fieldObject);
		// System.out.println(annotation);
		if (annotation.getTypeName().getFullyQualifiedName().equals("ManyToOne")) {
			AssociationObject association = new AssociationObject("ManyToOne-OneToMany", entityObject1, entityObject2,
					false);
			return association;
		} else if (annotation.getTypeName().getFullyQualifiedName().equals("OneToMany")) {
			AssociationObject association = new AssociationObject("ManyToOne-OneToMany", entityObject2, entityObject1,
					false);
			return association;
		} else if (annotation.getTypeName().getFullyQualifiedName().equals("ManyToMany")) {
			boolean entityObject1IsOwner = false;
			for (org.eclipse.jdt.core.dom.Annotation ann : fieldObject.getAnnotations()) {
				String name = ann.getTypeName().getFullyQualifiedName();
				if (name.equals("JoinTable")) {
					entityObject1IsOwner = true;
				}
			}
			if (entityObject1IsOwner) {
				AssociationObject association = new AssociationObject("ManyToMany", entityObject1, entityObject2,
						false);
				return association;
			} else {
				AssociationObject association = new AssociationObject("ManyToMany", entityObject2, entityObject1,
						false);
				return association;
			}

		} else {
			boolean entityObject1IsOwner = false;
			for (org.eclipse.jdt.core.dom.Annotation ann : fieldObject.getAnnotations()) {
				String name = ann.getTypeName().getFullyQualifiedName();
				if (name.equals("JoinColumn")) {
					entityObject1IsOwner = true;
				}
			}
			if (entityObject1IsOwner) {
				AssociationObject association = new AssociationObject("OneToOne", entityObject1, entityObject2, false);
				return association;
			} else {
				AssociationObject association = new AssociationObject("OneToOne", entityObject2, entityObject1, false);
				return association;
			}
		}
	}

	public boolean checkDependencyBetweenClasses(ClassObject classObject1, ClassObject classObject2) {
		ICompilationUnit cu1 = (ICompilationUnit) classObject1.getITypeRoot().getPrimaryElement();
		ICompilationUnit cu2 = (ICompilationUnit) classObject2.getITypeRoot().getPrimaryElement();
		if (cu1.getParent().getParent().equals(cu2.getParent().getParent())) {
			if (cu1.getParent().equals(cu2.getParent())) {
				if (classObject1.hasFieldType(classObject2.getName())) {
					return true;
				} else {
					if (findClassNameInsideClass(cu1, classObject2.getName())) {
						return true;
					} else {
						return false;
					}
				}
			} else {
				boolean hasPackageImport = false;

				ASTParser parser = ASTParser.newParser(ASTReader.JLS);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setSource(cu2);
				parser.setResolveBindings(true);
				CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

				String[] classNameArray = classObject1.getName().split("\\.");
				StringBuilder newstring = new StringBuilder();
				for (int i = 0; i < classNameArray.length - 1; i++) {
					newstring.append(classNameArray[i]);
					newstring.append(".");
				}
				String packageName = newstring.toString() + "*";
				for (Object o : astRoot.imports()) {
					ImportDeclaration importDeclaration = (ImportDeclaration) o;
					// System.out.println(importDeclaration.getName());
					// System.out.println(importDeclaration.getName()+" "+classObject2.getName());
					if (importDeclaration.getName().toString().equals(classObject1.getName())) {
						return true;
					} else if (importDeclaration.getName().toString().equals(packageName)) {
						hasPackageImport = true;
					}
				}
				if (hasPackageImport) {
					if (classObject1.hasFieldType(classObject2.getName())) {
						return true;
					} else {
						return false;
					}
				} else {
					return false;
				}
			}
		}
		return false;
	}

	public boolean findClassNameInsideClass(ICompilationUnit cu, String fullClassName) {
		String[] classNameArray = fullClassName.split("\\.");
		final String className = classNameArray[classNameArray.length - 1];
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		final Set<SimpleName> names = new HashSet<SimpleName>();
		astRoot.accept(new ASTVisitor() {
			public boolean visit(SimpleName node) {
				if (node.toString().equals(className)) {
					names.add(node);
				}
				return true;
			}
		});
		if (names.size() > 0) {
			return true;
		} else {
			return false;
		}
	}

	public boolean AddRestOfClassesToBeMovedAndCopied(List<ClassObject> newClasses) {
		Set<ClassObject> RestOfClassesToBeMoved = new HashSet<ClassObject>();
		Set<ClassObject> RestOfClassesToBeCopied = new HashSet<ClassObject>();
		boolean addedClass = false;
		for (ClassObject classOb : newClasses) {
			if ((!classOb.isTestClass()) && (!classesToBeMoved.contains(classOb))
					&& (!classesToBeCopied.contains(classOb)) && (!RestOfClassesToBeMoved.contains(classOb))
					&& (!RestOfClassesToBeCopied.contains(classOb)) && (!monolithClasses.contains(classOb))) {
				boolean hasDependenctWithMonoloith = false;
				for (ClassObject monolithClass : monolithClasses) {
					if (checkDependencyBetweenClasses(monolithClass, classOb)) {
						hasDependenctWithMonoloith = true;
					}
				}
				for (ClassObject obj : classesToBeMoved) {
					if ((checkDependencyBetweenClasses(obj, classOb) || checkDependencyBetweenClasses(classOb, obj))
							&& !hasDependenctWithMonoloith) {
						// System.out.println(classOb.getName());
						RestOfClassesToBeMoved.add(classOb);
						addedClass = true;
					}
				}
				for (ClassObject obj : classesToBeCopied) {
					if ((checkDependencyBetweenClasses(obj, classOb) || checkDependencyBetweenClasses(classOb, obj))
							&& !hasDependenctWithMonoloith) {
						// System.out.println(classOb.getName());
						RestOfClassesToBeCopied.add(classOb);
						addedClass = true;
					}
				}
			}
		}

		classesToBeMoved.addAll(RestOfClassesToBeMoved);
		classesToBeCopied.addAll(RestOfClassesToBeCopied);
		return addedClass;
	}

}
