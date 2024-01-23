package gr.uom.java.jdeodorant.refactoring.views;


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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.Access;
import gr.uom.java.ast.AssociationObject;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.CompilationUnitCache;
import gr.uom.java.ast.EntityObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.distance.CandidateRefactoring;
import gr.uom.java.distance.DistanceMatrix;
import gr.uom.java.distance.Entity;
import gr.uom.java.distance.ExtractClassCandidateRefactoring;
import gr.uom.java.distance.ExtractClassCandidateGroup;
import gr.uom.java.distance.ExtractedConcept;
import gr.uom.java.distance.MySystem;
import gr.uom.java.jdeodorant.preferences.PreferenceConstants;
import gr.uom.java.jdeodorant.refactoring.Activator;
import gr.uom.java.jdeodorant.refactoring.manipulators.ExtractClassRefactoring;
import gr.uom.java.jdeodorant.refactoring.manipulators.MoveClassRefactoring;
import gr.uom.java.jdeodorant.refactoring.views.CodeSmellPackageExplorer.CodeSmellType;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.part.*;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.*;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring.Mode;
import org.eclipse.jdt.internal.corext.refactoring.structure.*;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
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
	//classes of microservice to be extracted
	List<ClassObject> chosenClasses;
	List<ClassObject> monolithClasses;
	//map classesToBeMoved/classesToBeCopied with destination
	//Map<ClassObject, IJavaElement> map = new HashMap<ClassObject, IJavaElement>();
	List<ClassObject> classesToBeMoved = new ArrayList<ClassObject>();
	List<ClassObject> classesToBeCopied = new ArrayList<ClassObject>();
	//Accessibilities that may need changing
	Map<MethodObject,ClassObject> methodsAccessChange = new HashMap<MethodObject,ClassObject>();
	//find relations
	List<EntityObject> entityClasses = new ArrayList<EntityObject>();
	List<AssociationObject> associationObjects = new ArrayList<AssociationObject>();
	List<AssociationObject> associationObjectsToBeBroken;
	//List<Object[]> relations = new ArrayList<Object[]>();
	private Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges = new LinkedHashMap<ICompilationUnit, CompilationUnitChange>();

	class ViewContentProvider implements ITreeContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}
		public void dispose() {
		}
		public Object[] getElements(Object parent) {
			if(candidateRefactoringTable!=null) {
				return candidateRefactoringTable;
			}
			else {
				return new ExtractClassCandidateGroup[] {};
			}
		}
		public Object[] getChildren(Object arg0) {
			if (arg0 instanceof ExtractClassCandidateGroup) {
				return ((ExtractClassCandidateGroup) arg0).getExtractedConcepts().toArray();
			}
			else if(arg0 instanceof ExtractedConcept) {
				return ((ExtractedConcept) arg0).getConceptClusters().toArray();
			}
			else {
				return new CandidateRefactoring[] {};
			}
		}
		public Object getParent(Object arg0) {
			if(arg0 instanceof ExtractClassCandidateRefactoring) {
				return getParentConcept((ExtractClassCandidateRefactoring)arg0);
			}
			else if(arg0 instanceof ExtractedConcept) {
				return getParentCandidateGroup(((ExtractedConcept)arg0).getSourceClass());
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
					TreeSet<ExtractClassCandidateRefactoring> set = new TreeSet<ExtractClassCandidateRefactoring>(entry.getCandidates());
					return ""+set.first().getDistinctSourceDependencies() + "/" + set.first().getDistinctTargetDependencies();
				default:
					return "";
				}
			}
			else if(obj instanceof CandidateRefactoring) {
				ExtractClassCandidateRefactoring entry = (ExtractClassCandidateRefactoring)obj;
				switch(index) {
				case 0:
					return "Extract Class";
				case 2:
					return ""+entry.getTopics();
				case 3:
					return ""+entry.getDistinctSourceDependencies() + "/" + entry.getDistinctTargetDependencies();
				case 4:
					Integer userRate = ((ExtractClassCandidateRefactoring)entry).getUserRate();
					return (userRate == null) ? "" : userRate.toString();
				default:
					return "";
				}
			}
			else if(obj instanceof ExtractedConcept){
				ExtractedConcept entry = (ExtractedConcept)obj;
				switch(index) {
				case 1:
					return "      "+entry.getTopics();
				default:
					return "";
				}
			}
			else {
				return "";
			}
		}
		public Image getColumnImage(Object obj, int index) {
			Image image = null;
			if(obj instanceof ExtractClassCandidateRefactoring) {
				int rate = -1;
				Integer userRate = ((ExtractClassCandidateRefactoring)obj).getUserRate();
				if(userRate != null)
					rate = userRate;
				switch(index) {
				case 4:
					if(rate != -1) {
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
			if (obj1 instanceof CandidateRefactoring
					&& obj2 instanceof CandidateRefactoring) {
				ExtractClassCandidateRefactoring candidate1 = (ExtractClassCandidateRefactoring)obj1;
				ExtractClassCandidateRefactoring candidate2 = (ExtractClassCandidateRefactoring)obj2;
				return candidate1.compareTo(candidate2);
			} 
			else if(obj1 instanceof ExtractedConcept
					&& obj2 instanceof ExtractedConcept) {
				ExtractedConcept concept1 = (ExtractedConcept)obj1;
				ExtractedConcept concept2 = (ExtractedConcept)obj2;
				return concept1.compareTo(concept2);
			}
			else {
				ExtractClassCandidateGroup group1 = (ExtractClassCandidateGroup)obj1;
				ExtractClassCandidateGroup group2 = (ExtractClassCandidateGroup)obj2;
				return group1.compareTo(group2);
			}
		}
	}

	private ISelectionListener selectionListener = new ISelectionListener() {
		public void selectionChanged(IWorkbenchPart sourcepart, ISelection selection) {
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection)selection;
				Object element = structuredSelection.getFirstElement();
				IJavaProject javaProject = null;
				if(element instanceof IJavaProject) {
					javaProject = (IJavaProject)element;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
					selectedType = null;
				}
				else if(element instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot packageFragmentRoot = (IPackageFragmentRoot)element;
					javaProject = packageFragmentRoot.getJavaProject();
					selectedPackageFragmentRoot = packageFragmentRoot;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
					selectedType = null;
				}
				else if(element instanceof IPackageFragment) {
					IPackageFragment packageFragment = (IPackageFragment)element;
					javaProject = packageFragment.getJavaProject();
					selectedPackageFragment = packageFragment;
					selectedPackageFragmentRoot = null;
					selectedCompilationUnit = null;
					selectedType = null;
				}
				else if(element instanceof ICompilationUnit) {
					ICompilationUnit compilationUnit = (ICompilationUnit)element;
					javaProject = compilationUnit.getJavaProject();
					selectedCompilationUnit = compilationUnit;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedType = null;
				}
				else if(element instanceof IType) {
					IType type = (IType)element;
					javaProject = type.getJavaProject();
					selectedType = type;
					selectedPackageFragmentRoot = null;
					selectedPackageFragment = null;
					selectedCompilationUnit = null;
				}
				if(javaProject != null && !javaProject.equals(selectedProject)) {
					selectedProject = javaProject;
					/*if(candidateRefactoringTable != null)
						tableViewer.remove(candidateRefactoringTable);*/
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
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
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
				IStructuredSelection selection = (IStructuredSelection)treeViewer.getSelection();
				if(selection instanceof IStructuredSelection) {
					IStructuredSelection structuredSelection = (IStructuredSelection)selection;
					Object[] selectedItems = structuredSelection.toArray();
					if(selection.getFirstElement() instanceof ExtractClassCandidateRefactoring && selectedItems.length == 1) {
						ExtractClassCandidateRefactoring candidateRefactoring = (ExtractClassCandidateRefactoring)selection.getFirstElement();
						treeViewer.getTree().setMenu(getRightClickMenu(treeViewer, candidateRefactoring));
					}
				}
			}
		});
		
		treeViewer.expandAll();

		for (int i = 0, n = treeViewer.getTree().getColumnCount(); i < n; i++) {
			treeViewer.getTree().getColumn(i).pack();
		}

		treeViewer.setColumnProperties(new String[] {"type", "group", "source", "ep", "rate"});
		treeViewer.setCellEditors(new CellEditor[] {
				new TextCellEditor(), new TextCellEditor(), new TextCellEditor(), new TextCellEditor(),
				new MyComboBoxCellEditor(treeViewer.getTree(), new String[] {"0", "1", "2", "3", "4", "5"}, SWT.READ_ONLY)
		});

		treeViewer.setCellModifier(new ICellModifier() {
			public boolean canModify(Object element, String property) {
				return property.equals("rate");
			}

			public Object getValue(Object element, String property) {
				if(element instanceof ExtractClassCandidateRefactoring) {
					ExtractClassCandidateRefactoring candidate = (ExtractClassCandidateRefactoring)element;
					if(candidate.getUserRate() != null)
						return candidate.getUserRate();
					else
						return 0;
				}
				return 0;
			}

			public void modify(Object element, String property, Object value) {
				TreeItem item = (TreeItem)element;
				Object data = item.getData();
				if(data instanceof ExtractClassCandidateRefactoring) {
					ExtractClassCandidateRefactoring candidate = (ExtractClassCandidateRefactoring)data;
					candidate.setUserRate((Integer)value);
					IPreferenceStore store = Activator.getDefault().getPreferenceStore();
					boolean allowUsageReporting = store.getBoolean(PreferenceConstants.P_ENABLE_USAGE_REPORTING);
					if(allowUsageReporting) {
						Tree tree = treeViewer.getTree();
						int groupPosition = -1;
						int totalGroups = tree.getItemCount();
						int totalOpportunities = 0;
						for(int i=0; i<tree.getItemCount(); i++) {
							TreeItem treeItem = tree.getItem(i);
							ExtractClassCandidateGroup group = (ExtractClassCandidateGroup)treeItem.getData();
							if(group.getSource().equals(candidate.getSource())) {
								groupPosition = i;
							}
							totalOpportunities += group.getCandidates().size();
						}
						try {
							Set<VariableDeclaration> extractedFieldFragments = candidate.getExtractedFieldFragments();
							Set<MethodDeclaration> extractedMethods = candidate.getExtractedMethods();
							boolean allowSourceCodeReporting = store.getBoolean(PreferenceConstants.P_ENABLE_SOURCE_CODE_REPORTING);
							String declaringClass = candidate.getSourceClassTypeDeclaration().resolveBinding().getQualifiedName();
							String content = URLEncoder.encode("project_name", "UTF-8") + "=" + URLEncoder.encode(activeProject.getElementName(), "UTF-8");
							content += "&" + URLEncoder.encode("source_class_name", "UTF-8") + "=" + URLEncoder.encode(declaringClass, "UTF-8");
							String extractedElementsSourceCode = "";
							String extractedFieldsText = "";
							for(VariableDeclaration fieldFragment : extractedFieldFragments) {
								extractedFieldsText += fieldFragment.resolveBinding().toString() + "\n";
								extractedElementsSourceCode += fieldFragment.resolveBinding().toString() + "\n";
							}
							content += "&" + URLEncoder.encode("extracted_fields", "UTF-8") + "=" + URLEncoder.encode(extractedFieldsText, "UTF-8");
							String extractedMethodsText = "";
							for(MethodDeclaration method : extractedMethods) {
								extractedMethodsText += method.resolveBinding().toString() + "\n";
								extractedElementsSourceCode += method.toString() + "\n";
							}
							content += "&" + URLEncoder.encode("extracted_methods", "UTF-8") + "=" + URLEncoder.encode(extractedMethodsText, "UTF-8");
							content += "&" + URLEncoder.encode("group_position", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(groupPosition), "UTF-8");
							content += "&" + URLEncoder.encode("total_groups", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(totalGroups), "UTF-8");
							content += "&" + URLEncoder.encode("total_opportunities", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(totalOpportunities), "UTF-8");
							content += "&" + URLEncoder.encode("EP", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(0.0), "UTF-8");
							if(allowSourceCodeReporting)
								content += "&" + URLEncoder.encode("extracted_elements_source_code", "UTF-8") + "=" + URLEncoder.encode(extractedElementsSourceCode, "UTF-8");
							content += "&" + URLEncoder.encode("rating", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(candidate.getUserRate()), "UTF-8");
							content += "&" + URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(System.getProperty("user.name"), "UTF-8");
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
		getSite().getWorkbenchWindow().getWorkbench().getOperationSupport().getOperationHistory().addOperationHistoryListener(new IOperationHistoryListener() {
			public void historyNotification(OperationHistoryEvent event) {
				int eventType = event.getEventType();
				if(eventType == OperationHistoryEvent.UNDONE  || eventType == OperationHistoryEvent.REDONE ||
						eventType == OperationHistoryEvent.OPERATION_ADDED || eventType == OperationHistoryEvent.OPERATION_REMOVED) {
					if(activeProject != null && CompilationUnitCache.getInstance().getAffectedProjects().contains(activeProject)) {
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
				if(viewPart != null)
					page.hideView(viewPart);
				try {
					page.showView(CodeSmellVisualization.ID);
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
			public void widgetDefaultSelected(SelectionEvent arg) {}
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
				if(viewPart != null) {
					page.hideView(viewPart);
					wasAlreadyOpen = true;
				}
				activeProject = selectedProject;
				CompilationUnitCache.getInstance().clearCache();
				candidateRefactoringTable = getTable();
				treeViewer.setContentProvider(new ViewContentProvider());
				applyRefactoringAction.setEnabled(true);
				saveResultsAction.setEnabled(true);
				packageExplorerAction.setEnabled(true);
				if(wasAlreadyOpen)
					openPackageExplorerViewPart();
			}
		};
		identifyBadSmellsAction.setToolTipText("Identify Bad Smells");
		identifyBadSmellsAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		identifyBadSmellsAction.setEnabled(false);

		saveResultsAction = new Action() {
			public void run() {
				saveResults();
			}
		};
		saveResultsAction.setToolTipText("Save Results");
		saveResultsAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_ETOOL_SAVE_EDIT));
		saveResultsAction.setEnabled(false);

		packageExplorerAction = new Action(){
			public void run() {
				//open the Code Smell Package Explorer only if it is closed
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart viewPart = page.findView(CodeSmellPackageExplorer.ID);
				if(viewPart == null/* || !CodeSmellPackageExplorer.CODE_SMELL_TYPE.equals(CodeSmellType.GOD_CLASS)*/)
					openPackageExplorerViewPart();
			}
		};
		packageExplorerAction.setToolTipText("Code Smell Package Explorer");
		packageExplorerAction.setImageDescriptor(Activator.getImageDescriptor("/icons/" + "compass.png"));
		packageExplorerAction.setEnabled(false);

		applyRefactoringAction = new Action() {
			public void run() {
				IStructuredSelection selection = (IStructuredSelection)treeViewer.getSelection();
				if(selection != null && selection.getFirstElement() instanceof CandidateRefactoring) {
					CandidateRefactoring entry = (CandidateRefactoring)selection.getFirstElement();
					if(entry.getSourceClassTypeDeclaration() != null) {
						IFile sourceFile = entry.getSourceIFile();
						CompilationUnit sourceCompilationUnit = (CompilationUnit)entry.getSourceClassTypeDeclaration().getRoot();
						Refactoring refactoring = null;
						if(entry instanceof ExtractClassCandidateRefactoring) {
							ExtractClassCandidateRefactoring candidate = (ExtractClassCandidateRefactoring)entry;
							String[] tokens = candidate.getTargetClassName().split("\\.");
							String extractedClassName = tokens[tokens.length-1];
							Set<VariableDeclaration> extractedFieldFragments = candidate.getExtractedFieldFragments();
							Set<MethodDeclaration> extractedMethods = candidate.getExtractedMethods();
							IPreferenceStore store = Activator.getDefault().getPreferenceStore();
							boolean allowUsageReporting = store.getBoolean(PreferenceConstants.P_ENABLE_USAGE_REPORTING);
							if(allowUsageReporting) {
								Tree tree = treeViewer.getTree();
								int groupPosition = -1;
								int totalGroups = tree.getItemCount();
								int totalOpportunities = 0;
								for(int i=0; i<tree.getItemCount(); i++) {
									TreeItem treeItem = tree.getItem(i);
									ExtractClassCandidateGroup group = (ExtractClassCandidateGroup)treeItem.getData();
									if(group.getSource().equals(candidate.getSource())) {
										groupPosition = i;
									}
									totalOpportunities += group.getCandidates().size();
								}
								try {
									boolean allowSourceCodeReporting = store.getBoolean(PreferenceConstants.P_ENABLE_SOURCE_CODE_REPORTING);
									String declaringClass = candidate.getSourceClassTypeDeclaration().resolveBinding().getQualifiedName();
									String content = URLEncoder.encode("project_name", "UTF-8") + "=" + URLEncoder.encode(activeProject.getElementName(), "UTF-8");
									content += "&" + URLEncoder.encode("source_class_name", "UTF-8") + "=" + URLEncoder.encode(declaringClass, "UTF-8");
									String extractedElementsSourceCode = "";
									String extractedFieldsText = "";
									for(VariableDeclaration fieldFragment : extractedFieldFragments) {
										extractedFieldsText += fieldFragment.resolveBinding().toString() + "\n";
										extractedElementsSourceCode += fieldFragment.resolveBinding().toString() + "\n";
									}
									content += "&" + URLEncoder.encode("extracted_fields", "UTF-8") + "=" + URLEncoder.encode(extractedFieldsText, "UTF-8");
									String extractedMethodsText = "";
									for(MethodDeclaration method : extractedMethods) {
										extractedMethodsText += method.resolveBinding().toString() + "\n";
										extractedElementsSourceCode += method.toString() + "\n";
									}
									content += "&" + URLEncoder.encode("extracted_methods", "UTF-8") + "=" + URLEncoder.encode(extractedMethodsText, "UTF-8");
									content += "&" + URLEncoder.encode("group_position", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(groupPosition), "UTF-8");
									content += "&" + URLEncoder.encode("total_groups", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(totalGroups), "UTF-8");
									content += "&" + URLEncoder.encode("total_opportunities", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(totalOpportunities), "UTF-8");
									content += "&" + URLEncoder.encode("EP", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(0.0), "UTF-8");
									if(allowSourceCodeReporting)
										content += "&" + URLEncoder.encode("extracted_elements_source_code", "UTF-8") + "=" + URLEncoder.encode(extractedElementsSourceCode, "UTF-8");
									content += "&" + URLEncoder.encode("application", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8");
									content += "&" + URLEncoder.encode("application_selected_name", "UTF-8") + "=" + URLEncoder.encode(extractedClassName, "UTF-8");
									content += "&" + URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(System.getProperty("user.name"), "UTF-8");
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
							
							/*refactoring = new ExtractClassRefactoring(sourceFile, sourceCompilationUnit,
									candidate.getSourceClassTypeDeclaration(),
									extractedFieldFragments, extractedMethods,
									candidate.getDelegateMethods(), extractedClassName);*/
						}
						try {
							//Add public access modifier to methods whose class will be extracted to microservice
							for(MethodObject methodObject:methodsAccessChange.keySet()) {
								addPublicAccesModifier(methodObject);
							}
							//Creating the microservice package
							ICompilationUnit  chosenCl = (ICompilationUnit)classesToBeMoved.get(0).getITypeRoot().getPrimaryElement();
							IFolder McFolder = chosenCl.getJavaProject().getPackageFragments()[2].getCorrespondingResource().getProject().getFolder("/src/main/java/com/mgiandia/Microservice");
						    if(!McFolder.exists()) {
						    	McFolder.create(true, true, null);
						    }
						    //Copying classes to be copied
							for(int j=0;j<classesToBeCopied.size();j++) {
								copyClass(classesToBeCopied.get(j));
							}
							//Update the imports of classes to be moved to the new copied classes
							for(ClassObject toMove:classesToBeMoved) {
								for(ClassObject toCopy:classesToBeCopied) {
									updateImportsForClassesToBeMovedToTheCopied(toMove,toCopy);
								}
							}
							//Grouping classes to be moved per package
							List<String> destinationNames = new ArrayList<String>();
							Map<ICompilationUnit,String> classMap = new HashMap<ICompilationUnit,String>();
							for(ClassObject ob:classesToBeMoved) {
								ICompilationUnit  cuTemp = (ICompilationUnit)ob.getITypeRoot().getPrimaryElement();
								String location= cuTemp.getCorrespondingResource().getParent().getName();
								classMap.put(cuTemp, location);
								if(!destinationNames.contains(location)) {
									destinationNames.add(location);
								}
							}
							for(String s:destinationNames) {
								List<ICompilationUnit> cus = new ArrayList<ICompilationUnit>();
								for(ICompilationUnit c:classMap.keySet()) {
									if(classMap.get(c).equals(s)) {
										cus.add(c);
									}
								}
								RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE);
							    IFolder processFolder = cus.get(0).getJavaProject().getPackageFragments()[2].getCorrespondingResource().getProject().getFolder("/src/main/java/com/mgiandia/Microservice/"
							    +s);
							    if(!processFolder.exists()) {
							    	processFolder.create(true, true, null);
							    }
								MoveDescriptor descriptor = (MoveDescriptor)contribution.createDescriptor();
								IJavaElement pack = JavaCore.create(processFolder);
								ICompilationUnit[] moved =new ICompilationUnit[cus.size()];
								moved = cus.toArray(moved);
								RefactoringStatus status = new RefactoringStatus();
								MoveClassRefactoring moveclassRefactoring = new MoveClassRefactoring(descriptor, moved, cus.get(0).getResource().getProject().getName(), pack, true, status);
								refactoring = moveclassRefactoring.getRefactoring();
								IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
								JavaUI.openInEditor(classesToBeCopied.get(0).getITypeRoot().getPrimaryElement());
								IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
								page.closeAllEditors(true);
								MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, applyRefactoringAction);
								RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard); 
								String titleForFailedChecks = ""; //$NON-NLS-1$ 
								op.run(getSite().getShell(), titleForFailedChecks);
								
							}
							/*ICompilationUnit  cu = (ICompilationUnit)classesToBeMoved.get(0).getITypeRoot().getPrimaryElement();
							RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE);
						    IFolder processFolder = cu.getJavaProject().getPackageFragments()[2].getCorrespondingResource().getProject().getFolder("/src/main/java/com/mgiandia/Microservice/"
						    +cu.getCorrespondingResource().getParent().getName());
						    if(!processFolder.exists()) {
						    	processFolder.create(true, true, null);
						    }
							MoveDescriptor descriptor = (MoveDescriptor)contribution.createDescriptor();
							IJavaElement pack = JavaCore.create(processFolder);
							ICompilationUnit[] moved = {cu};
							RefactoringStatus status = new RefactoringStatus();
							MoveClassRefactoring moveclassRefactoring = new MoveClassRefactoring(descriptor, moved, cu.getResource().getProject().getName(), pack, true, status);
							refactoring = moveclassRefactoring.getRefactoring();
							//IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
							//JavaUI.openInEditor(sourceJavaElement);
							MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, applyRefactoringAction);
							RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard); 
							String titleForFailedChecks = ""; //$NON-NLS-1$ 
							op.run(getSite().getShell(), titleForFailedChecks);*/
							
						} catch (PartInitException e) {
							e.printStackTrace();
						} catch (JavaModelException e) {
							e.printStackTrace();
						} catch (CoreException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch(InterruptedException e) {
							e.printStackTrace();
						} catch (MalformedTreeException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
				}
			}
		};
		applyRefactoringAction.setToolTipText("Apply Refactoring");
		applyRefactoringAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_DEF_VIEW));
		applyRefactoringAction.setEnabled(false);

		doubleClickAction = new Action() {
			public void run() {
				IStructuredSelection selection = (IStructuredSelection)treeViewer.getSelection();
				if(selection.getFirstElement() instanceof CandidateRefactoring) {
					CandidateRefactoring candidate = (CandidateRefactoring)selection.getFirstElement();
					if(candidate.getSourceClassTypeDeclaration() != null) {
						IFile sourceFile = candidate.getSourceIFile();
						try {
							IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
							ITextEditor sourceEditor = (ITextEditor)JavaUI.openInEditor(sourceJavaElement);
							List<Position> positions = candidate.getPositions();
							AnnotationModel annotationModel = (AnnotationModel)sourceEditor.getDocumentProvider().getAnnotationModel(sourceEditor.getEditorInput());
							Iterator<Annotation> annotationIterator = annotationModel.getAnnotationIterator();
							while(annotationIterator.hasNext()) {
								Annotation currentAnnotation = annotationIterator.next();
								if(currentAnnotation.getType().equals(SliceAnnotation.EXTRACTION)) {
									annotationModel.removeAnnotation(currentAnnotation);
								}
							}
							Position firstPosition = null;
							Position lastPosition = null;
							int minOffset = Integer.MAX_VALUE;
							int maxOffset = -1;
							for(Position position : positions) {
								SliceAnnotation annotation = new SliceAnnotation(SliceAnnotation.EXTRACTION, candidate.getAnnotationText());
								annotationModel.addAnnotation(annotation, position);
								if(position.getOffset() < minOffset) {
									minOffset = position.getOffset();
									firstPosition = position;
								}
								if(position.getOffset() > maxOffset) {
									maxOffset = position.getOffset();
									lastPosition = position;
								}
							}
							int offset = firstPosition.getOffset();
							int length = lastPosition.getOffset() + lastPosition.getLength() - firstPosition.getOffset();
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
			if(ASTReader.getSystemObject() != null && activeProject.equals(ASTReader.getExaminedProject())) {
				new ASTReader(activeProject, ASTReader.getSystemObject(), null);
			}
			else {
				ps.busyCursorWhile(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						try {
							new ASTReader(activeProject, monitor);
						} catch (CompilationErrorDetectedException e) {
							Display.getDefault().asyncExec(new Runnable() {
								public void run() {
									MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), MESSAGE_DIALOG_TITLE,
											"Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
								}
							});
						}
					}
				});
			}
			SystemObject systemObject = ASTReader.getSystemObject();
			/*while(systemObject.getClassListIterator().hasNext()) {
				ClassObject classOb = systemObject.getClassListIterator().next().getClassObject();
				if (classOb.getAnnotations().size()>0) {
					for (String annotation : classOb.getAnnotations()) {
						if (annotation.equals("Entity")) {
							System.out.println(classOb.getName()+" is Entity");
						}
					}
				}
			}*/
			if(systemObject != null) {
				Set<ClassObject> classObjectsToBeExamined = new LinkedHashSet<ClassObject>();
				if(selectedPackageFragmentRoot != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedPackageFragmentRoot));
				}
				else if(selectedPackageFragment != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedPackageFragment));
				}
				else if(selectedCompilationUnit != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedCompilationUnit));
				}
				else if(selectedType != null) {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects(selectedType));
				}
				else {
					classObjectsToBeExamined.addAll(systemObject.getClassObjects());
				}
				List<ClassObject> classes = new ArrayList<ClassObject>();
				classes.addAll(systemObject.getClassObjects());
				//TEMPORARY
				chosenClasses = new ArrayList<ClassObject>();
				monolithClasses = new ArrayList<ClassObject>();
				for(final ClassObject classOb : classes) {
					if((classOb.getName().equals("com.mgiandia.library.domain.Book"))||(classOb.getName().equals("com.mgiandia.library.domain.Author"))||(classOb.getName().equals("com.mgiandia.library.domain.Publisher"))) {
						chosenClasses.add(classOb);
					}else if((classOb.getITypeRoot().getParent().getElementName().equals(selectedType.getTypeRoot().getParent().getElementName()))&&(!classOb.containsMethodWithTestAnnotation())) {
						monolithClasses.add(classOb);
					}
				}
				for(final ClassObject classOb : classes) {
								//MoveRefactoring ref= new MoveRefactoring();
								//final MoveResourceChange ref= MoveResourceChange.create(classOb.getITypeRoot().getResource(),classOb.getITypeRoot().getResource().getParent().getParent());
								/*ps.busyCursorWhile(new IRunnableWithProgress() {
									public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
										try {
											//classOb.getITypeRoot().getResource().move(classOb.getIFile().getParent().getParent().getFullPath(), true, monitor);
											ref.perform(monitor);
										} catch (OperationCanceledException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} catch (CoreException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								});*/
							
					/*if((classOb.getITypeRoot().getParent().getElementName().equals(selectedType.getTypeRoot().getParent().getElementName()))&&(!isEntity)&&(!isTest)) {
						System.out.println(classOb.getName()+" is not Entity "+classOb.getITypeRoot().getParent().getElementName());
						classesToBeCopied.add(classOb);
					}*/
					//Adding Methods and their class with none as access and are used by a class that is not in the chosen classes to be extracted
					if(!classOb.isTestClass()&&(!chosenClasses.contains(classOb))) {
						for(MethodObject method:classOb.getMethodList()) {
							for(MethodInvocationObject methodInvocation: method.getMethodInvocations()) {
								for(ClassObject c3:chosenClasses) {
									if(methodInvocation.getOriginClassName().equals(c3.getName())) {
										for(MethodObject m:c3.getMethodList()) {
											if((m.getName().equals(methodInvocation.getMethodName()))&&(m.getAccess().equals(Access.NONE))) {
												System.out.println(classOb.getName()+"          "+ method.getName()+"     "+methodInvocation+"       "+methodInvocation.getOriginClassName());
												methodsAccessChange.put(m, c3);
											}
										}
									}
								}
							}
						}
					}
					//Creating EntityObjects
					if(classOb.isEntity()) {
						EntityObject entityObject = new EntityObject(classOb);
						Iterator iter = classOb.getFieldIterator();
						while(iter.hasNext()) {
								FieldObject fieldObject = (FieldObject) iter.next();
								for(org.eclipse.jdt.core.dom.Annotation ann:fieldObject.getAnnotations()) {
									String name = ann.getTypeName().getFullyQualifiedName();
									if((name.equals("OneToMany")||(name.equals("ManyToOne"))||(name.equals("OneToOne"))||(name.equals("ManyToMany")))) {
										entityObject.addAssociatedObject(fieldObject);
										fieldObject.getType().toString();
										System.out.println(classOb.getName()+"    "+fieldObject.getType().getGenericType());
									}
									if(name.equals("Id")) {
										entityObject.setIdField(fieldObject);
									}
									//System.out.println(ann.getTypeName().getFullyQualifiedName());
								}
						}
						entityClasses.add(entityObject);
					}
					System.out.println("-------------------------");
					
					for(ClassObject cl:chosenClasses) {
						//Classes that need to be copied
						if((!chosenClasses.contains(classOb))&&(cl.hasFieldType(classOb.getName()))&&(!classOb.isEntity())&&(!classOb.isTestClass())){	
							//System.out.println("We need to copy "+classOb.getName());
							classesToBeCopied.add(classOb);
						}
						//Classes that need to be moved
						if(classesToBeCopied.contains(classOb)) {
							boolean hasDependency = false;
							for(ClassObject monolithClass: monolithClasses) {
								if(monolithClass.hasFieldType(classOb.getName())) {
									hasDependency = true;
								}
							}
							if(!hasDependency) {
								classesToBeCopied.remove(classOb);
								classesToBeMoved.add(classOb);
							}
							
						}
					}
				}
				List<ClassObject> ExtraclassesToBeCopied= new ArrayList<ClassObject>();
				for(ClassObject cl2:classesToBeCopied) {
					for(ClassObject classOb:classes) {
						if(cl2.hasFieldType(classOb.getName())&&(!classesToBeCopied.contains(classOb))) {
							System.out.println(classOb.getName());
							ExtraclassesToBeCopied.add(classOb);
						}
					}
				}
				for(ClassObject ob:ExtraclassesToBeCopied) {
					classesToBeCopied.add(ob);
				}
				for(ClassObject obj:chosenClasses) {
					classesToBeMoved.add(obj);
				}
				//Creating AssociationObjects
				associationObjects = new ArrayList<AssociationObject>();
				for(EntityObject entityObject1: entityClasses) {
					for(FieldObject fieldObject:entityObject1.getAssociatedObjects()) {
						for(EntityObject entityObject2: entityClasses) {
							if(fieldObject.getType().getClassType().equals(entityObject2.getClassObject().getName())) {
								if(!checkIfAssociationExists(entityObject1,entityObject2)) {
									AssociationObject associationObject = createAssociationObject(entityObject1,entityObject2);
									associationObjects.add(associationObject);
									//System.out.println(entityObject1.getClassObject().getName()+"    "+entityObject2.getClassObject().getName()+"    "+associationObject.getType());
								}
							}else if(fieldObject.getType().getGenericType()!=null) {
								if(fieldObject.getType().getGenericType().equals("<"+entityObject2.getClassObject().getName()+">")) {
									if(!checkIfAssociationExists(entityObject1,entityObject2)) {
										AssociationObject associationObject = createAssociationObject(entityObject1,entityObject2);
										associationObjects.add(associationObject);
										//System.out.println(entityObject1.getClassObject().getName()+"    "+entityObject2.getClassObject().getName()+"    "+associationObject.getType());
									}
								}
							}
						}
					}
				}
				//AssociationObjects that need to be broken
				associationObjectsToBeBroken = new ArrayList<AssociationObject>();
				for(AssociationObject association: associationObjects) {
					ClassObject classObject1 = association.getOwnerClass().getClassObject();
					ClassObject classObject2 = association.getOwnedClass().getClassObject();
					if((classesToBeMoved.contains(classObject1)&&!classesToBeMoved.contains(classObject2))||((classesToBeMoved.contains(classObject2)&&!classesToBeMoved.contains(classObject1)))) {
						associationObjectsToBeBroken.add(association);
						System.out.println(association.getOwnerClass().getClassObject().getName()+"     "+association.getOwnedClass().getClassObject().getName()+"    "+association.isBidirectional());
					}
					//System.out.println(association.getOwnerClass().getClassObject().getName()+"     "+association.getOwnedClass().getClassObject().getName()+"    "+association.isBidirectional());
				}
				final AssociationObject association = associationObjectsToBeBroken.get(0);
				//Owned Class Service Extraction
				IFile sourceFile = association.getOwnedClass().getClassObject().getIFile();
				ICompilationUnit cu = (ICompilationUnit)association.getOwnedClass().getClassObject().getITypeRoot().getPrimaryElement();
				//final List<ExtractClassCandidateRefactoring> extractClassCandidateL = new ArrayList<ExtractClassCandidateRefactoring>();
				//List<String> list = Arrays.asList( association.getOwnedClass().getClassObject().getName());
				//MySystem system1 = new MySystem(systemObject, true);
				//final DistanceMatrix distanceMatrix1 = new DistanceMatrix(system1);
				//extractClassCandidateL.addAll(distanceMatrix1.getExtractClassCandidateRefactorings(new HashSet<String>(Arrays.asList(association.getOwnedClass().getClassObject().getName())), null));
		        //CandidateRefactoring entry = (CandidateRefactoring)extractClassCandidateL.get(0);
		        //CompilationUnit sourceCompilationUnit = (CompilationUnit)entry.getSourceClassTypeDeclaration().getRoot();
				//TypeDeclaration sourceTypeDeclaration = (TypeDeclaration)association.getOwnedClass().getClassObject().getAbstractTypeDeclaration();
				
				FieldObject fielsObj = association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject());
				System.out.println(fielsObj.getType().toString()+" "+fielsObj.getName());
				String fieldName = fielsObj.getName();
				boolean isSet;
				String fieldType;
				if(fielsObj.getType().getGenericType()!=null){
					String[] fullFieldName = fielsObj.getType().getGenericType().toString().split("\\.");
					fieldType = fullFieldName[fullFieldName.length-1];
					fieldType = fieldType.substring(0, fieldType.length() - 1);
					isSet = true;
				}else {
					String[] fullFieldName = fielsObj.getType().toString().split("\\.");
					fieldType = fullFieldName[fullFieldName.length-1];
					isSet = false;
				}
				String[] typeName = association.getOwnedClass().getIdField().getType().toString().split("\\.");
		        final String FKtype=typeName[typeName.length-1];
		        
		        org.eclipse.jdt.core.dom.Annotation joinColumnAnnotation = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()).getAnnotations().get(1);
	            String s = new StringBuilder().append('"').toString();
	            String[] arr = joinColumnAnnotation.toString().split(s);
		        final String FKfieldName = arr[1];
				
				Set<VariableDeclaration> extractedFieldFragments = new LinkedHashSet<VariableDeclaration>();
				extractedFieldFragments.add(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()).getVariableDeclaration());
				final Set<MethodDeclaration> extractedMethods = association.getOwnedClass().getMethodDeclarationsByField(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()));
				Set<MethodDeclaration> delegateMethods = new LinkedHashSet<MethodDeclaration>();
				String[] array = association.getOwnerClass().getClassObject().getName().split("\\.");
				final String extractedClassName = array[array.length-1]+"Service";

				final Set<String> extractedMethodNamesWithThisExpression = changeThisExpressionToIDInMethodInvocations(cu,extractedMethods,association);
				ASTParser parser2 = ASTParser.newParser(ASTReader.JLS);
				parser2.setKind(ASTParser.K_COMPILATION_UNIT);
				parser2.setSource(cu);
		        parser2.setResolveBindings(true);
		        
		        
		        final Set<MethodDeclaration> newExtractedMethods = new HashSet<MethodDeclaration>();
				CompilationUnit sourceCompilationUnit = (CompilationUnit) parser2.createAST(null);
				
				//createQueryMethod(sourceCompilationUnit);
				//createFactoryMethod(sourceCompilationUnit);
				
				sourceCompilationUnit.accept(new ASTVisitor() {
				    public boolean visit(MethodDeclaration node) {
				    	for(MethodDeclaration methodDeclaration:extractedMethods) {
				    		if(methodDeclaration.getName().toString().equals(node.getName().toString())) {
				    			newExtractedMethods.add(node);
				    		}
				    	}
				    	return true;
				    }
				});
				TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) sourceCompilationUnit.types().get(0);
				System.out.println(newExtractedMethods);
				Refactoring refactoring = new ExtractClassRefactoring(sourceFile, sourceCompilationUnit,
						sourceTypeDeclaration,
						extractedFieldFragments, newExtractedMethods,
						delegateMethods, extractedClassName,isSet,fieldName,fieldType,FKfieldName,FKtype);
				try {
					IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
					JavaUI.openInEditor(sourceJavaElement);
				} catch (PartInitException e) {
					e.printStackTrace();
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
				MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, applyRefactoringAction);
				RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard); 
				try { 
					String titleForFailedChecks = ""; //$NON-NLS-1$ 
					op.run(getSite().getShell(), titleForFailedChecks); 
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
				
				MethodDeclaration test = null;
				for(MethodDeclaration md:newExtractedMethods) {
					test = md;
					break;
				}
				System.out.println(test);
				
				
				
				/*int[] selection = {test.getStartPosition(), test.getLength()};
				InlineMethodRefactoring refactoring2 = InlineMethodRefactoring.create(sourceCompilationUnit.getTypeRoot(), new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(sourceCompilationUnit.getTypeRoot(), true), selection[0], selection[1]);
				refactoring2.setDeleteSource(true);
				try {
					refactoring2.setCurrentMode(Mode.INLINE_ALL);
					IProgressMonitor pm = new NullProgressMonitor();
					RefactoringStatus res = refactoring.checkInitialConditions(pm);
					res = refactoring.checkFinalConditions(pm);
					final PerformRefactoringOperation oper = new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
					oper.run(new NullProgressMonitor());
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (OperationCanceledException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				
				//Adding FK to Owner Class
				ICompilationUnit cuOwner = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
				ASTParser parser = ASTParser.newParser(ASTReader.JLS);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setSource(cuOwner);
		        parser.setResolveBindings(true);
		        
		        MultiTextEdit sourceMultiTextEdit = new MultiTextEdit();
		        CompilationUnitChange sourceCompilationUnitChange = new CompilationUnitChange("", cuOwner);
		        sourceCompilationUnitChange.setEdit(sourceMultiTextEdit);
		        compilationUnitChanges.put(cuOwner, sourceCompilationUnitChange);
		        
		        
		        
		        FieldObject fieldObject = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject());
		        final FieldDeclaration fieldDeclaration = (FieldDeclaration)fieldObject.getVariableDeclaration().getParent();
				final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
				astRoot.recordModifications();
				final ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
				astRoot.accept(new ASTVisitor() {
				    public boolean visit(FieldDeclaration node) {
				        // Find the FieldDeclaration node that you want to replace
				        if (node.toString().equals(fieldDeclaration.toString())) {
				            // Create a new FieldDeclaration node with the desired changes
				            FieldDeclaration newFieldDeclaration = astRoot.getAST().newFieldDeclaration(astRoot.getAST().newVariableDeclarationFragment());
				            Type type = astRoot.getAST().newSimpleType(astRoot.getAST().newName(FKtype));
				            newFieldDeclaration.setType(type);
				            ListRewrite listRewrite = rewriter.getListRewrite(newFieldDeclaration, FieldDeclaration.MODIFIERS2_PROPERTY);
				            listRewrite.insertLast(astRoot.getAST().newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD), null);
				            //newFieldDeclaration.modifiers().addAll(node.modifiers());
				            SimpleName newName = astRoot.getAST().newSimpleName(FKfieldName);
				            
				            ((VariableDeclarationFragment) newFieldDeclaration.fragments().get(0)).setName(newName);
				            
				            NormalAnnotation annotation = astRoot.getAST().newNormalAnnotation();
				            //annotation.setTypeName(astRoot.getAST().newSimpleName("@Column(name=\""+arr[1]+"\")"));
				            annotation.setTypeName(astRoot.getAST().newSimpleName("Column"));
				            MemberValuePair pair = astRoot.getAST().newMemberValuePair();
				            pair.setName(astRoot.getAST().newSimpleName("name"));
				            StringLiteral literal = astRoot.getAST().newStringLiteral();
				            literal.setLiteralValue(FKfieldName);
				            pair.setValue(literal);
				            annotation.values().add(pair);
				            rewriter.getListRewrite(newFieldDeclaration, FieldDeclaration.MODIFIERS2_PROPERTY).insertFirst(annotation, null);

				            // Replace the old FieldDeclaration node with the new one
				            //TypeDeclaration typeDeclaration = (TypeDeclaration) astRoot.types().get(0);
				            //ListRewrite listRewriteTypeDecl = rewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				            rewriter.replace(node, newFieldDeclaration, null);
				        }
				        return true;
				    }
				});
				
				String previousTypeFull[] = fieldObject.getType().toString().split("\\.");
				final String previousType=previousTypeFull[previousTypeFull.length-1];
				Set<MethodDeclaration> extractedMethods2 = association.getOwnerClass().getMethodDeclarationsByField(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()));
		        MethodDeclaration[] methodDeclarations = extractedMethods2.toArray(new MethodDeclaration[extractedMethods2.size()]);
		        for(int i=0;i<methodDeclarations.length;i++) {
		        	MethodDeclaration methodDeclaration = methodDeclarations[i];
		        	ReplaceFieldWithFieldIdInsideMethod(cuOwner,methodDeclaration,FKtype,FKfieldName,fieldObject.getName(),previousType);
		        	ChangeReturnTypeForMethod(cuOwner,methodDeclaration,FKtype);
		        }
		        
		        
				TextEdit edits;
				try {
					edits = rewriter.rewriteAST();
					ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
					CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
					change.getEdit().addChild(edits);
					change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
					IProgressMonitor monitor = new NullProgressMonitor();
					change.perform(monitor);
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				extractOwnerServiceClass(association,FKfieldName,FKtype);
				
				for(final ClassObject classObject :classes) {
					ICompilationUnit icu = (ICompilationUnit)classObject.getITypeRoot().getPrimaryElement();
					//ICompilationUnit icu = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
					
					MultiTextEdit sourceMultiTextEdit2 = new MultiTextEdit();
			        CompilationUnitChange sourceCompilationUnitChange2 = new CompilationUnitChange("", icu);
			        sourceCompilationUnitChange2.setEdit(sourceMultiTextEdit2);
			        compilationUnitChanges.put(icu, sourceCompilationUnitChange2);
					
					ASTParser parser3 = ASTParser.newParser(ASTReader.JLS);
					parser3.setKind(ASTParser.K_COMPILATION_UNIT);
					parser3.setSource(icu);
			        parser3.setResolveBindings(true);
			        final CompilationUnit astRoot3 = (CompilationUnit) parser3.createAST(null);
					astRoot3.recordModifications();
					final ASTRewrite rewriter3 = ASTRewrite.create(astRoot3.getAST());
					final String extractedClassFieldName = Character.toLowerCase(extractedClassName.charAt(0)) + extractedClassName.substring(1);
					
					
					List<MethodDeclaration> classMethodDeclarations = new ArrayList<MethodDeclaration>();
					for(MethodObject methObj:classObject.getMethodList()) {
						classMethodDeclarations.add(methObj.getMethodDeclaration());
					}
					
					astRoot3.accept(new ASTVisitor() {
						
						public boolean visit(final MethodDeclaration md) {
							md.accept(new ASTVisitor() {
								//boolean hasMoreThanOneInvocationOfExtractedMethod = false;
								//boolean hasMoreThanOneInvocationOfExtractedMethod2 = false;
								public boolean visit(MethodInvocation mi) {
									for(MethodDeclaration methodDeclaration:newExtractedMethods) {
										if(mi.getName().toString().equals(methodDeclaration.getName().toString())) {
											//if(!hasMoreThanOneInvocationOfExtractedMethod) {
												//hasMoreThanOneInvocationOfExtractedMethod = true;
												VariableDeclarationFragment fragment = astRoot3.getAST().newVariableDeclarationFragment();
												fragment.setName(astRoot3.getAST().newSimpleName(extractedClassFieldName));
												MethodInvocation invocation = astRoot3.getAST().newMethodInvocation();
												invocation.setName(astRoot3.getAST().newSimpleName("factoryMethod"));
												invocation.setExpression(astRoot3.getAST().newSimpleName(extractedClassName));
												//TypeDeclaration type = (TypeDeclaration) methodDeclaration.getParent();
												
												addExpressionAsArgumentToInvocation(astRoot3,mi,invocation,association,rewriter3);
												
												Type serviceType = astRoot3.getAST().newSimpleType(astRoot3.getAST().newName(extractedClassName));
												fragment.setInitializer(invocation);
												VariableDeclarationStatement statement = astRoot3.getAST().newVariableDeclarationStatement(fragment);
												statement.setType(serviceType);
												System.out.println(findFirstStatement(mi).getParent());
												Block block = (Block) findFirstStatement(mi).getParent();
												ListRewrite listRewrite = rewriter3.getListRewrite(block, Block.STATEMENTS_PROPERTY);
												listRewrite.insertBefore(statement, findFirstStatement(mi), null);
											//}
											for(String name:extractedMethodNamesWithThisExpression) {
												if(name.equals(mi.getName().toString())) {
													addExpressionAsArgumentToInvocation(astRoot3,mi,mi,association,rewriter3);
												}
											}
											rewriter3.replace(mi.getExpression(), astRoot3.getAST().newSimpleName(extractedClassFieldName), null);
										}
									}
									if(mi.getName().toString().equals("getBook")) {
										//if(!hasMoreThanOneInvocationOfExtractedMethod2) {
											//hasMoreThanOneInvocationOfExtractedMethod2 = true;
											VariableDeclarationFragment fragment = astRoot3.getAST().newVariableDeclarationFragment();
											fragment.setName(astRoot3.getAST().newSimpleName("bookService"));
											MethodInvocation invocation = astRoot3.getAST().newMethodInvocation();
											invocation.setName(astRoot3.getAST().newSimpleName("factoryMethod"));
											invocation.setExpression(astRoot3.getAST().newSimpleName("BookService"));
											ListRewrite listRewrite2 = rewriter3.getListRewrite(invocation, MethodInvocation.ARGUMENTS_PROPERTY);
											listRewrite2.insertLast(mi, null);
											
											Type serviceType = astRoot3.getAST().newSimpleType(astRoot3.getAST().newName("BookService"));
											fragment.setInitializer(invocation);
											VariableDeclarationStatement statement = astRoot3.getAST().newVariableDeclarationStatement(fragment);
											statement.setType(serviceType);
											Block block = (Block) findFirstStatement(mi).getParent();
											ListRewrite listRewrite = rewriter3.getListRewrite(block, Block.STATEMENTS_PROPERTY);
											listRewrite.insertBefore(statement, findFirstStatement(mi), null);
										//}
										MethodInvocation newExpression = astRoot3.getAST().newMethodInvocation();
										newExpression.setExpression(astRoot3.getAST().newSimpleName("bookService"));
										newExpression.setName(astRoot3.getAST().newSimpleName("queryBook"));
										rewriter3.replace(mi, newExpression, null);
									}
									return true;
								}
							});;
							return true;
						}
					});
					TextEdit edits2;
					try {
						edits2 = rewriter3.rewriteAST();
						icu = (ICompilationUnit)astRoot3.getJavaElement();
						CompilationUnitChange change = compilationUnitChanges.get(icu);
						change.getEdit().addChild(edits2);
						change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits2}));
						IProgressMonitor monitor = new NullProgressMonitor();
						change.perform(monitor);
					} catch (JavaModelException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (CoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					//for(MethodDeclaration md:classMethodDeclarations) {
						//md.accept(new ASTVisitor() {
						astRoot3.accept(new ASTVisitor() {
							boolean hasMoreThanOneInvocationOfExtractedMethod = false;
							boolean hasMoreThanOneInvocationOfExtractedMethod2 = false;
							public boolean visit(MethodInvocation node) {
								for(MethodDeclaration methodDeclaration:newExtractedMethods) {
									if(node.getName().toString().equals(methodDeclaration.getName().toString())) {
										//System.out.println(node.toString()+" "+node.getExpression());
		
										if(!hasMoreThanOneInvocationOfExtractedMethod) {
											hasMoreThanOneInvocationOfExtractedMethod = true;
											TypeDeclaration classDeclaration = (TypeDeclaration) astRoot3.types().get(0);
											ListRewrite listRewrite = rewriter3.getListRewrite(classDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
											final FieldDeclaration fieldDeclaration3 = astRoot3.getAST().newFieldDeclaration(astRoot3.getAST().newVariableDeclarationFragment());
											fieldDeclaration3.setType(astRoot3.getAST().newSimpleType(astRoot3.getAST().newName(extractedClassName)));
											fieldDeclaration3.modifiers().add(astRoot3.getAST().newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
											((VariableDeclarationFragment) fieldDeclaration3.fragments().get(0)).setName(astRoot3.getAST().newSimpleName(extractedClassFieldName));
											ClassInstanceCreation classInstane = astRoot3.getAST().newClassInstanceCreation();
											classInstane.setType(astRoot3.getAST().newSimpleType(astRoot3.getAST().newName(extractedClassName)));
											//variableDeclarationFragment.setInitializer(classInstane);
											((VariableDeclarationFragment) fieldDeclaration3.fragments().get(0)).setInitializer(classInstane);
											//fieldDeclaration.fragments().add(variableDeclarationFragment);
											listRewrite.insertFirst(fieldDeclaration3, null);
										}
										
										
										Expression exp = node.getExpression();
										String typeName = null;
										ITypeBinding typeBinding = node.getExpression().resolveTypeBinding();
										if (typeBinding != null) {
										    typeName = typeBinding.getQualifiedName();
										    //System.out.println(typeName);
										}
										if(typeName.equals(association.getOwnedClass().getClassObject().getName())) {
											MethodInvocation newExpression = astRoot3.getAST().newMethodInvocation();
											/*if ((exp.toString().contains("this"))) {
						                    	 FieldAccess newFieldAccess = astRoot.getAST().newFieldAccess();
						                         newFieldAccess.setExpression(astRoot.getAST().newThisExpression());
						                         newFieldAccess.setName(astRoot.getAST().newSimpleName(association.getOwnedClass().getIdField().getName()));
						                         newExpression.setExpression(newFieldAccess);
						                         newExpression.setName(astRoot.getAST().newSimpleName("getBook"));
											}else {*/
												//Expression exp = ast.newExpression();
												//exp = node.getExpression();
												//Expression exp2 = (Expression) exp;
												String getterMethodName = association.getOwnedClass().getIdFieldGetterName();
												newExpression.setExpression(astRoot3.getAST().newSimpleName(exp.toString()));
												newExpression.setName(astRoot3.getAST().newSimpleName(getterMethodName));
											//}
											
											ListRewrite listRewrite2 = rewriter3.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
											listRewrite2.insertLast(newExpression, null);
										}else {
											ListRewrite listRewrite2 = rewriter3.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
											listRewrite2.insertLast(node.getExpression(), null);
										}
										
										//FieldAccess newFieldAccess = astRoot.getAST().newFieldAccess();
				                        //newFieldAccess.setExpression(astRoot.getAST().newThisExpression());
				                        //newFieldAccess.setName();
				                        rewriter3.replace(node.getExpression(), astRoot3.getAST().newSimpleName(extractedClassFieldName), null);
										/*node.getParent().accept(new ASTVisitor() {
											public boolean visit(SimpleName node2) {
												System.out.println(node2.toString());
												return true;
											}
										});*/
									}
									
									
								}
								
								if(node.getName().toString().equals("getBook")) {
									System.out.println(classObject.getName()+"  "+node.toString()+" "+node.getExpression());
									
									if(!hasMoreThanOneInvocationOfExtractedMethod2) {
										hasMoreThanOneInvocationOfExtractedMethod2 = true;
										TypeDeclaration classDeclaration = (TypeDeclaration) astRoot3.types().get(0);
										ListRewrite listRewrite = rewriter3.getListRewrite(classDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
										final FieldDeclaration fieldDeclaration3 = astRoot3.getAST().newFieldDeclaration(astRoot3.getAST().newVariableDeclarationFragment());
										fieldDeclaration3.setType(astRoot3.getAST().newSimpleType(astRoot3.getAST().newName("BookService")));
										fieldDeclaration3.modifiers().add(astRoot3.getAST().newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
										((VariableDeclarationFragment) fieldDeclaration3.fragments().get(0)).setName(astRoot3.getAST().newSimpleName("bookService"));
										ClassInstanceCreation classInstane = astRoot3.getAST().newClassInstanceCreation();
										classInstane.setType(astRoot3.getAST().newSimpleType(astRoot3.getAST().newName("BookService")));
										//variableDeclarationFragment.setInitializer(classInstane);
										((VariableDeclarationFragment) fieldDeclaration3.fragments().get(0)).setInitializer(classInstane);
										//fieldDeclaration.fragments().add(variableDeclarationFragment);
										listRewrite.insertFirst(fieldDeclaration3, null);
									}
									MethodInvocation newExpression = astRoot3.getAST().newMethodInvocation();
									newExpression.setExpression(astRoot3.getAST().newSimpleName("bookService"));
									newExpression.setName(astRoot3.getAST().newSimpleName("queryBook"));
									ListRewrite listRewrite2 = rewriter3.getListRewrite(newExpression, MethodInvocation.ARGUMENTS_PROPERTY);
									listRewrite2.insertLast(node, null);
									rewriter3.replace(node, newExpression, null);
									
								}
								
								return true;
							}
						});
						/*TextEdit edits2;
						try {
							edits2 = rewriter3.rewriteAST();
							icu = (ICompilationUnit)astRoot3.getJavaElement();
							CompilationUnitChange change = compilationUnitChanges.get(icu);
							change.getEdit().addChild(edits2);
							change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits2}));
							IProgressMonitor monitor = new NullProgressMonitor();
							change.perform(monitor);
						} catch (JavaModelException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (CoreException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}*/
					}
				//}
				
				
				
				
				/**/
				
		        
		        /*for (SimpleName simpleName : simpleNames) {
		            rewrite.replace(simpleName, ast.newSimpleName("id"), null);
		        }*/
		        //TextEdit edits = rewrite.rewriteAST();
		        
		        /*List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
		        SingleVariableDeclaration newParameter = astRoot.getAST().newSingleVariableDeclaration();
		        newParameter.setType(astRoot.getAST().newPrimitiveType(PrimitiveType.INT));
		        newParameter.setName(astRoot.getAST().newSimpleName("newParam"));*/

		        // Replace the old parameter with the new one
		        //parameters.set(0, newParameter);
		        //ListRewrite parametersRewrite = rewriter.getListRewrite(astRoot.findDeclaringNode(methodDeclaration.resolveBinding().getKey()), MethodDeclaration.PARAMETERS_PROPERTY);
		         //rewriter.getListRewrite(methodDeclaration, MethodDeclaration.PARAMETERS_PROPERTY);
				//parametersRewrite.insertLast(newParameter, null);
		        
		        //rewriter.replace(methodDeclaration, methodDeclaration, null);

		        // Update the compilation unit
		        
				/*for(ClassObject toMove:classesToBeMoved) {
					for(ClassObject toCopy:classesToBeCopied) {
						ICompilationUnit cu = (ICompilationUnit)toMove.getITypeRoot().getPrimaryElement();
						ASTParser parser = ASTParser.newParser(ASTReader.JLS);
						parser.setKind(ASTParser.K_COMPILATION_UNIT);
						parser.setSource(cu);
				        parser.setResolveBindings(true);
						CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
						astRoot.recordModifications();
						//System.out.println(astRoot.imports());
						ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
						for (Object o : astRoot.imports()) {
					        ImportDeclaration importDeclaration = (ImportDeclaration) o;
					        //System.out.println(importDeclaration.getName().getFullyQualifiedName()+"  "+toCopy.getName());
					        if (importDeclaration.getName().getFullyQualifiedName().equals(toCopy.getName())) {
					        	System.out.println(toMove.getName()+"  "+toCopy.getName());
					        	ListRewrite listRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
					        	String[] arr = toCopy.getName().split("\\.");
					        	System.out.println("hello                        "+arr.length);
					        	ImportDeclaration id = astRoot.getAST().newImportDeclaration();
					        	id.setName(astRoot.getAST().newName(new String[] {arr[0], arr[1], "Microservice", arr[3], arr[4]}));
					            listRewrite.replace(importDeclaration,id, null);
					            ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
					            TextEdit importEdits = importRewrite.rewriteImports(null);
					            TextEdit edits = rewriter.rewriteAST();
					            edits.addChild(importEdits);
					            Document document = new Document(cu.getSource());
					            edits.apply(document);
					            cu.getBuffer().setContents(document.get());
					            cu.save(null, true);
					        }
					    }
					}
				}*/
				
				
				/*ICompilationUnit  cu = selectedType.getCompilationUnit();
				RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE);
				//RefactoringDescriptor descriptor=contribution.createDescriptor();
				IPackageFragment[] roots = selectedType.getTypeRoot().getJavaProject().getPackageFragments();
				System.out.println(roots[11].getElementName());
				MoveDescriptor descriptor = (MoveDescriptor)contribution.createDescriptor();
				descriptor.setProject(cu.getResource().getProject().getName( ));
				//descriptor.setDestination(cu.getResource().getParent()); // new name for a Class
				descriptor.setDestination((IJavaElement)roots[11]);
				//System.out.println(brFile.getITypeRoot().getResource().getParent().getParent());
				descriptor.setUpdateReferences(true);
				ICompilationUnit[] moved = {cu};
				IFile[] files = {};
				IFolder[] folders = {};
				descriptor.setMoveResources(files, folders, moved);
				RefactoringStatus status = new RefactoringStatus();
				try {
				    Refactoring refactoring = descriptor.createRefactoring(status);

				    IProgressMonitor monitor = new NullProgressMonitor();
				    refactoring.checkInitialConditions(monitor);
				    refactoring.checkFinalConditions(monitor);
				    Change change = refactoring.createChange(monitor);
				    change.perform(monitor);

				} catch (CoreException e) {
				    // TODO Auto-generated catch block
				    e.printStackTrace();
				} catch (Exception e) {
				    // TODO Auto-generated catch block
				    e.printStackTrace();
				}*/
				final Set<String> classNamesToBeExamined = new LinkedHashSet<String>();
				for(ClassObject classObject : classObjectsToBeExamined) {
					if(!classObject.isEnum() && !classObject.isInterface() && !classObject.isGeneratedByParserGenenator())
						classNamesToBeExamined.add(classObject.getName());
				}
				MySystem system = new MySystem(systemObject, true);
				final DistanceMatrix distanceMatrix = new DistanceMatrix(system);
				final List<ExtractClassCandidateRefactoring> extractClassCandidateList = new ArrayList<ExtractClassCandidateRefactoring>();

				/*ps.busyCursorWhile(new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						extractClassCandidateList.addAll(distanceMatrix.getExtractClassCandidateRefactorings(classNamesToBeExamined, monitor));
					}
				});*/
				HashMap<String, ExtractClassCandidateGroup> groupedBySourceClassMap = new HashMap<String, ExtractClassCandidateGroup>();
				for(ExtractClassCandidateRefactoring candidate : extractClassCandidateList) {
					if(groupedBySourceClassMap.keySet().contains(candidate.getSourceEntity())) {
						groupedBySourceClassMap.get(candidate.getSourceEntity()).addCandidate(candidate);
					}
					else {
						ExtractClassCandidateGroup group = new ExtractClassCandidateGroup(candidate.getSourceEntity());
						group.addCandidate(candidate);
						groupedBySourceClassMap.put(candidate.getSourceEntity(), group);
					}
				}
				for(String sourceClass : groupedBySourceClassMap.keySet()) {
					groupedBySourceClassMap.get(sourceClass).groupConcepts();
				}

				table = new ExtractClassCandidateGroup[groupedBySourceClassMap.values().size()];
				int counter = 0;
				for(ExtractClassCandidateGroup candidate : groupedBySourceClassMap.values()) {
					table[counter] = candidate;
					counter++;
				}
			}
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (CompilationErrorDetectedException e) {
			MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), MESSAGE_DIALOG_TITLE,
					"Compilation errors were detected in the project. Fix the errors before using JDeodorant.");
		/*} catch (JavaModelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();*/
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		/*} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();*/
		}
		return table;		
	}

	private ExtractClassCandidateGroup getParentCandidateGroup(String sourceClass) {
		String[] classes = new String[candidateRefactoringTable.length];
		for(int i=0; i<candidateRefactoringTable.length; i++) {
			classes[i] = candidateRefactoringTable[i].getSource();
		}
		for(int i=0; i<classes.length; i++) {
			if(classes[i].equals(sourceClass)) {
				return candidateRefactoringTable[i];
			}
		}
		return null;
	}

	private ExtractedConcept getParentConcept(ExtractClassCandidateRefactoring candidate) {
		for(int i=0; i<candidateRefactoringTable.length; i++) {
			for(ExtractedConcept concept : candidateRefactoringTable[i].getExtractedConcepts()) {
				HashSet<Entity> copiedConceptEntities = new HashSet<Entity>(concept.getConceptEntities());
				copiedConceptEntities.retainAll(candidate.getExtractedEntities());
				if(!copiedConceptEntities.isEmpty()) {
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
		if(selected != null) {
			try {
				BufferedWriter out = new BufferedWriter(new FileWriter(selected));
				Tree tree = treeViewer.getTree();
				/*TreeColumn[] columns = tree.getColumns();
				for(int i=0; i<columns.length; i++) {
					if(i == columns.length-1)
						out.write(columns[i].getText());
					else
						out.write(columns[i].getText() + "\t");
				}
				out.newLine();*/
				for(int i=0; i<tree.getItemCount(); i++) {
					TreeItem treeItem = tree.getItem(i);
					ExtractClassCandidateGroup group = (ExtractClassCandidateGroup)treeItem.getData();
					for(CandidateRefactoring candidate : group.getCandidates()) {
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
			ArrayList<CandidateRefactoring> candidates= new ArrayList<CandidateRefactoring>();
			for(ExtractClassCandidateGroup group: candidateRefactoringTable){
				ArrayList<ExtractClassCandidateRefactoring> extractCandidates = group.getCandidates();
				candidates.addAll(extractCandidates);
			}
			CodeSmellVisualizationDataSingleton.setCandidates((CandidateRefactoring[]) candidates.toArray(new CandidateRefactoring[candidates.size()]));
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IViewPart viewPart = page.findView(CodeSmellPackageExplorer.ID);
			CodeSmellPackageExplorer.CODE_SMELL_TYPE = CodeSmellType.MICROSERVICE_EXTRACTION;
			if(viewPart != null)
				page.hideView(viewPart);
			page.showView(CodeSmellPackageExplorer.ID);

		} catch (PartInitException e) {
			e.printStackTrace();
		}
	}

	public void setSelectedLine(CandidateRefactoring candidateRefactoring) {
		Tree tree = treeViewer.getTree();
		for(int i=0; i< tree.getItemCount(); i++){
			TreeItem treeItem = tree.getItem(i);
			ExtractClassCandidateGroup group = (ExtractClassCandidateGroup)treeItem.getData();
			if(group.getCandidates().contains(candidateRefactoring)) {
				treeItem.setExpanded(true);
				treeViewer.refresh();
				setSelectedLineWithinCandidateGroup(tree, treeItem, candidateRefactoring);
				break;
			}
		}
	}
	
	private void setSelectedLineWithinCandidateGroup(Tree tree, TreeItem candidateGroupTreeItem, CandidateRefactoring candidateRefactoring) {
		for(int i=0; i<candidateGroupTreeItem.getItemCount(); i++){
			TreeItem conceptTreeItem = candidateGroupTreeItem.getItem(i);
			ExtractedConcept concept = (ExtractedConcept)conceptTreeItem.getData();
			if(concept.getConceptClusters().contains(candidateRefactoring)) {
				conceptTreeItem.setExpanded(true);
				treeViewer.refresh();
				for(int j=0; j<conceptTreeItem.getItemCount(); j++) {
					TreeItem candidateTreeItem = conceptTreeItem.getItem(j);
					CandidateRefactoring candidate = (CandidateRefactoring)candidateTreeItem.getData();
					if(candidate.equals(candidateRefactoring)) {
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
		ICompilationUnit cu = (ICompilationUnit)methodsAccessChange.get(methodObject).getITypeRoot().getPrimaryElement();
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		astRoot.recordModifications();
		System.out.println(methodObject.getName());
		System.out.println(astRoot.findDeclaringNode(methodObject.getMethodDeclaration().resolveBinding().getKey()));
		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		ListRewrite listRewrite = rewriter.getListRewrite(astRoot.findDeclaringNode(methodObject.getMethodDeclaration().resolveBinding().getKey()), MethodDeclaration.MODIFIERS2_PROPERTY);
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
		ICompilationUnit  cuCopy = (ICompilationUnit)classObject.getITypeRoot().getPrimaryElement();
		IFolder processFolder2;
		try {
			processFolder2 = cuCopy.getJavaProject().getPackageFragments()[2].getCorrespondingResource().getProject().getFolder("/src/main/java/com/mgiandia/Microservice/"
			+cuCopy.getCorrespondingResource().getParent().getName());
			if(!processFolder2.exists()) {
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
	
	public void updateImportsForClassesToBeMovedToTheCopied(ClassObject toMove,ClassObject toCopy) {
		ICompilationUnit cu = (ICompilationUnit)toMove.getITypeRoot().getPrimaryElement();
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
		astRoot.recordModifications();
		//System.out.println(astRoot.imports());
		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
			try {
				if(toMove.getITypeRoot().getParent().equals(toCopy.getITypeRoot().getParent())&&(toMove.hasFieldType(toCopy.getName())||toCopy.hasFieldType(toMove.getName()))) {
					ListRewrite listRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
					ImportDeclaration newImport = astRoot.getAST().newImportDeclaration();
					String[] arr = toCopy.getName().split("\\.");
					newImport.setName(astRoot.getAST().newName(new String[] {arr[0], arr[1], "Microservice", arr[3], arr[4]}));
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
				}
				for (Object o : astRoot.imports()) {
			        ImportDeclaration importDeclaration = (ImportDeclaration) o;
			        //System.out.println(importDeclaration.getName().getFullyQualifiedName()+"  "+toCopy.getName());
			        if (importDeclaration.getName().getFullyQualifiedName().equals(toCopy.getName())) {
			        	System.out.println(toMove.getName()+"  "+toCopy.getName());
			        	ListRewrite listRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
			        	String[] arr = toCopy.getName().split("\\.");
			        	//System.out.println("hello                        "+arr.length);
			        	ImportDeclaration id = astRoot.getAST().newImportDeclaration();
			        	id.setName(astRoot.getAST().newName(new String[] {arr[0], arr[1], "Microservice", arr[3], arr[4]}));
			            listRewrite.replace(importDeclaration,id, null);
			            ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
			            TextEdit importEdits = importRewrite.rewriteImports(null);
			            TextEdit edits = rewriter.rewriteAST();
			            edits.addChild(importEdits);
			            Document document = new Document(cu.getSource());
			            edits.apply(document);
			            cu.getBuffer().setContents(document.get());
			            cu.save(null, true);
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
	
	public boolean checkIfAssociationExists(EntityObject entityObject1,EntityObject entityObject2) {
		boolean associationAlreadyExists=false;
		for(AssociationObject association: associationObjects) {
			if((association.getOwnerClass().equals(entityObject1)&&association.getOwnedClass().equals(entityObject2))
					||(association.getOwnerClass().equals(entityObject2)&&association.getOwnedClass().equals(entityObject1))) {
				associationAlreadyExists=true;
				association.setBidirectional(true);
			}
		}
		return associationAlreadyExists;
	}
	
	public AssociationObject createAssociationObject(EntityObject entityObject1,EntityObject entityObject2) {
		FieldObject fieldObject = entityObject1.getAssociatedObjectByClass(entityObject2.getClassObject());
		org.eclipse.jdt.core.dom.Annotation annotation = entityObject1.getAssociatedObjectAnnotationByField(fieldObject);
		//System.out.println(annotation);
		if(annotation.getTypeName().getFullyQualifiedName().equals("ManyToOne")) {
			AssociationObject association = new AssociationObject("ManyToOne-OneToMany",entityObject1,entityObject2,false);
			return association;
		}else if(annotation.getTypeName().getFullyQualifiedName().equals("OneToMany")) {
			AssociationObject association = new AssociationObject("ManyToOne-OneToMany",entityObject2,entityObject1,false);
			return association;
		}else if(annotation.getTypeName().getFullyQualifiedName().equals("ManyToMany")) {
			AssociationObject association = new AssociationObject("ManyToMany",entityObject1,entityObject2,false);
			return association;
		}else {
			AssociationObject association = new AssociationObject("OneToOne",entityObject1,entityObject2,false);
			return association;
		}
	}
	
	public void extractOwnerServiceClass(AssociationObject association,String FKfieldName, String FKtype) {
		IFile sourceFile = association.getOwnerClass().getClassObject().getIFile();
		ICompilationUnit cu = (ICompilationUnit)association.getOwnerClass().getClassObject().getITypeRoot().getPrimaryElement();
		//final List<ExtractClassCandidateRefactoring> extractClassCandidateL = new ArrayList<ExtractClassCandidateRefactoring>();
		//List<String> list = Arrays.asList( association.getOwnedClass().getClassObject().getName());
		//MySystem system1 = new MySystem(systemObject, true);
		//final DistanceMatrix distanceMatrix1 = new DistanceMatrix(system1);
		//extractClassCandidateL.addAll(distanceMatrix1.getExtractClassCandidateRefactorings(new HashSet<String>(Arrays.asList(association.getOwnedClass().getClassObject().getName())), null));
        //CandidateRefactoring entry = (CandidateRefactoring)extractClassCandidateL.get(0);
        //CompilationUnit sourceCompilationUnit = (CompilationUnit)entry.getSourceClassTypeDeclaration().getRoot();
		//TypeDeclaration sourceTypeDeclaration = (TypeDeclaration)association.getOwnedClass().getClassObject().getAbstractTypeDeclaration();
		
		FieldObject fielsObj = association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject());
		String fieldName = fielsObj.getName();
		boolean isSet;
		String fieldType;
		if(fielsObj.getType().getGenericType()!=null){
			String[] fullFieldName = fielsObj.getType().getGenericType().toString().split("\\.");
			fieldType = fullFieldName[fullFieldName.length-1];
			fieldType = fieldType.substring(0, fieldType.length() - 1);
			isSet = true;
		}else {
			String[] fullFieldName = fielsObj.getType().toString().split("\\.");
			fieldType = fullFieldName[fullFieldName.length-1];
			isSet = false;
		}
		
		Set<VariableDeclaration> extractedFieldFragments = new LinkedHashSet<VariableDeclaration>();
		extractedFieldFragments.add(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()).getVariableDeclaration());
		final Set<MethodDeclaration> extractedMethods = association.getOwnerClass().getMethodDeclarationsByField(association.getOwnerClass().getAssociatedObjectByClass(association.getOwnedClass().getClassObject()));
		Set<MethodDeclaration> delegateMethods = new LinkedHashSet<MethodDeclaration>();
		String[] arr = association.getOwnedClass().getClassObject().getName().split("\\.");
		final String className = arr[arr.length-1];
		String extractedClassName = className+"Service";
		
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        
        //final Set<MethodDeclaration> extractedMethods = new HashSet<MethodDeclaration>();
        
        final Set<MethodDeclaration> newExtractedMethods = new HashSet<MethodDeclaration>();
		CompilationUnit sourceCompilationUnit = (CompilationUnit) parser.createAST(null);
		sourceCompilationUnit.accept(new ASTVisitor() {
		    public boolean visit(MethodDeclaration node) {
		    	for(MethodDeclaration methodDeclaration:extractedMethods) {
		    		if((methodDeclaration.getName().toString().equals(node.getName().toString()))&&(!methodDeclaration.getName().toString().equals("get"+className)
		    				&&(!methodDeclaration.getName().toString().equals("set"+className)))) {
		    			newExtractedMethods.add(node);
		    		}
		    	}
		    	return true;
		    }
		});
		TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) sourceCompilationUnit.types().get(0);
		
		Refactoring refactoring = new ExtractClassRefactoring(sourceFile, sourceCompilationUnit,
				sourceTypeDeclaration,
				extractedFieldFragments, newExtractedMethods,
				delegateMethods, extractedClassName,isSet,fieldName,fieldType,FKfieldName,FKtype);
		try {
			IJavaElement sourceJavaElement = JavaCore.create(sourceFile);
			JavaUI.openInEditor(sourceJavaElement);
		} catch (PartInitException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		MyRefactoringWizard wizard = new MyRefactoringWizard(refactoring, applyRefactoringAction);
		RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard); 
		try { 
			String titleForFailedChecks = ""; //$NON-NLS-1$ 
			op.run(getSite().getShell(), titleForFailedChecks); 
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public Set<String> changeThisExpressionToIDInMethodInvocations(ICompilationUnit cu,Set<MethodDeclaration>extractedMethods,final AssociationObject association) {
		//ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		//parser.setKind(ASTParser.K_COMPILATION_UNIT);
		//parser.setSource(cu);
        //parser.setResolveBindings(true);
        //Set<MethodDeclaration> extractedMethods = association.getOwnedClass().getMethodDeclarationsByField(association.getOwnedClass().getAssociatedObjectByClass(association.getOwnerClass().getClassObject()));
        //final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
        //final ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		final Set<String> methodNames = new HashSet<String>();
        MethodDeclaration[] methodDeclarations = extractedMethods.toArray(new MethodDeclaration[extractedMethods.size()]);
        for(int i=0;i<methodDeclarations.length;i++) {
        	final MethodDeclaration methodDeclaration = methodDeclarations[i];
	        System.out.println(methodDeclarations[i].getName());
	        final AST ast = methodDeclarations[i].getAST();
	        final ASTRewrite rewrite = ASTRewrite.create(ast);
	        //final List<SimpleName> simpleNames = new ArrayList<SimpleName>();
	        methodDeclaration.accept(new ASTVisitor() {
	            @Override
	            public boolean visit(MethodInvocation node) {
	                List<Expression> arguments = node.arguments();
	                for (Expression argument : arguments) {
	                    if (argument instanceof ThisExpression) {
	                    	 methodNames.add(methodDeclaration.getName().toString());
	                    	 FieldAccess newFieldAccess = ast.newFieldAccess();
	                         newFieldAccess.setExpression(ast.newThisExpression());
	                         newFieldAccess.setName(ast.newSimpleName(association.getOwnedClass().getIdField().getName()));
	                        //Name thisName = ast.newSimpleName("this.id");
	                        //Expression replacement = ast.newQualifiedName(id);
	                        rewrite.replace(argument, newFieldAccess, null);
	                    }
	                }
	                return super.visit(node);
	            }
	        });
	        TextEdit edits;
			try {
				edits = rewrite.rewriteAST();
				Document document = new Document(cu.getSource());
				edits.apply(document);
				cu.getBuffer().setContents(document.get());
				cu.save(null, true);
				cu.commitWorkingCopy(true, null);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return methodNames;
	}
	
	
	public void ChangeReturnTypeForMethod(ICompilationUnit cu,MethodDeclaration methodDeclaration,String returnTypeName) {
		//final MethodDeclaration methodDeclaration = methodDeclarations[i];
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
    	final AST ast = methodDeclaration.getAST();
        final ASTRewrite rewriter = ASTRewrite.create(ast);
        Type returnType = methodDeclaration.getReturnType2();
        if(returnType.toString().equals("Book")) {
        	Type type = ast.newSimpleType(ast.newName(returnTypeName));
        	rewriter.replace(returnType, type, null);
        }
        TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void ReplaceFieldWithFieldIdInsideMethod(ICompilationUnit cu,final MethodDeclaration methodDeclaration,String type,final String fieldName,final String previousFieldName,String previousType) {
		//final MethodDeclaration methodDeclaration = methodDeclarations[i];
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu);
        parser.setResolveBindings(true);
        final CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);
    	final AST ast = methodDeclaration.getAST();
        final ASTRewrite rewriter = ASTRewrite.create(ast);
        for (Object obj : methodDeclaration.parameters()) {
            SingleVariableDeclaration param = (SingleVariableDeclaration) obj;
            if (param.getType().toString().equals(previousType)) {
                SingleVariableDeclaration newParam = methodDeclaration.getAST().newSingleVariableDeclaration();
                //
                newParam.setType(methodDeclaration.getAST().newSimpleType(methodDeclaration.getAST().newName(type)));
                newParam.setName(methodDeclaration.getAST().newSimpleName(fieldName));
                //methodDeclaration.parameters().set(methodDeclaration.parameters().indexOf(param), newParam);
                rewriter.replace(param, newParam, null);
            }
        }
        methodDeclaration.accept(new ASTVisitor() {
            public boolean visit(SimpleName name) {
                if (name.getIdentifier().equals(previousFieldName)) {
                    SimpleName newName = methodDeclaration.getAST().newSimpleName(fieldName);
                    rewriter.replace(name, newName, null);
                }
                return true;
            }
        });
        TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			ICompilationUnit sourceICompilationUnit = (ICompilationUnit)astRoot.getJavaElement();
			CompilationUnitChange change = compilationUnitChanges.get(sourceICompilationUnit);
			change.getEdit().addChild(edits);
			change.addTextEditGroup(new TextEditGroup("Change access of extracted member", new TextEdit[] {edits}));
			//cu.save(null, true);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	public void createFactoryMethod(CompilationUnit astRoot) {
		
		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		
		FieldDeclaration entityManagerDecl = astRoot.getAST().newFieldDeclaration(astRoot.getAST().newVariableDeclarationFragment());
		entityManagerDecl.setType(astRoot.getAST().newSimpleType(astRoot.getAST().newName("EntityManager")));
		entityManagerDecl.modifiers().add(astRoot.getAST().newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		((VariableDeclarationFragment) entityManagerDecl.fragments().get(0)).setName(astRoot.getAST().newSimpleName("em"));
		NormalAnnotation annotation = astRoot.getAST().newNormalAnnotation();
		annotation.setTypeName(astRoot.getAST().newSimpleName("Inject"));
		ListRewrite modifiers = rewriter.getListRewrite(entityManagerDecl, FieldDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertFirst(annotation, null);
		
		
		MethodDeclaration factoryMethod  = astRoot.getAST().newMethodDeclaration();
		factoryMethod.setName(astRoot.getAST().newSimpleName("factoryMethod"));
		Type returnType = astRoot.getAST().newSimpleType(astRoot.getAST().newName("ItemService"));
		factoryMethod.setReturnType2(returnType);
		factoryMethod.modifiers().add(astRoot.getAST().newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		factoryMethod.modifiers().add(astRoot.getAST().newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		
		SingleVariableDeclaration svd = astRoot.getAST().newSingleVariableDeclaration();
		svd.setName(astRoot.getAST().newSimpleName("bookno"));
		svd.setType(astRoot.getAST().newSimpleType(astRoot.getAST().newName("Integer")));
		
		VariableDeclarationStatement vds = astRoot.getAST().newVariableDeclarationStatement(astRoot.getAST().newVariableDeclarationFragment());
		vds.setType(astRoot.getAST().newSimpleType(astRoot.getAST().newName("ItemService")));
		//vds.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.NONE));
		
		VariableDeclarationFragment fragment = astRoot.getAST().newVariableDeclarationFragment();
		fragment.setName(astRoot.getAST().newSimpleName("itemService"));

		ClassInstanceCreation classInstanceCreation = astRoot.getAST().newClassInstanceCreation();
		classInstanceCreation.setType(astRoot.getAST().newSimpleType(astRoot.getAST().newName("ItemService")));

		fragment.setInitializer(classInstanceCreation);
		vds.fragments().add(fragment);
		vds.fragments().remove(0);
		
		factoryMethod.parameters().add(svd);
		
		
		Assignment assignment = astRoot.getAST().newAssignment();
		FieldAccess fieldAccess = astRoot.getAST().newFieldAccess();
		fieldAccess.setExpression(astRoot.getAST().newSimpleName("itemService"));
		fieldAccess.setName(astRoot.getAST().newSimpleName("items"));
		
		
		
		MethodInvocation createQuery = astRoot.getAST().newMethodInvocation();
	    createQuery.setExpression(astRoot.getAST().newSimpleName("em"));
	    createQuery.setName(astRoot.getAST().newSimpleName("createQuery"));
	    StringLiteral query = astRoot.getAST().newStringLiteral();
	    query.setLiteralValue("select i from Item i where i.bookno = :bookno");
	    createQuery.arguments().add(query);
	    TypeLiteral typeLiteral = astRoot.getAST().newTypeLiteral();
	    typeLiteral.setType(astRoot.getAST().newSimpleType(astRoot.getAST().newSimpleName("Item")));
	    createQuery.arguments().add(typeLiteral);
	    MethodInvocation setParameter = astRoot.getAST().newMethodInvocation();
	    setParameter.setExpression(createQuery);
	    setParameter.setName(astRoot.getAST().newSimpleName("setParameter"));
	    StringLiteral parameterName = astRoot.getAST().newStringLiteral();
	    parameterName.setLiteralValue("bookno");
	    setParameter.arguments().add(parameterName);
	    SimpleName ownedClassId = astRoot.getAST().newSimpleName("bookno");
	    setParameter.arguments().add(ownedClassId);
	    MethodInvocation getResultList = astRoot.getAST().newMethodInvocation();
	    getResultList.setExpression(setParameter);
	    getResultList.setName(astRoot.getAST().newSimpleName("getResultList"));
	    
	    ClassInstanceCreation classInstanceCreation2 = astRoot.getAST().newClassInstanceCreation();
	    Type returnType2 = astRoot.getAST().newSimpleType(astRoot.getAST().newName("HashSet"));
	    ParameterizedType parameterizedType2 = astRoot.getAST().newParameterizedType(returnType2);
	    parameterizedType2.typeArguments().add(astRoot.getAST().newSimpleType(astRoot.getAST().newName("Item")));
	    returnType2 = parameterizedType2;
	    classInstanceCreation2.setType(returnType2);
	    classInstanceCreation2.arguments().add(getResultList);
		
		
		
		assignment.setRightHandSide(classInstanceCreation2);
		assignment.setLeftHandSide(fieldAccess);

		
		Block block = astRoot.getAST().newBlock();
		block.statements().add(vds);
		ExpressionStatement expressionStatement = astRoot.getAST().newExpressionStatement(assignment);
		block.statements().add(expressionStatement);
	    ReturnStatement returnStatement = astRoot.getAST().newReturnStatement();
	    returnStatement.setExpression(astRoot.getAST().newSimpleName("itemService"));
	    block.statements().add(returnStatement);
	    
	    factoryMethod.setBody(block);
	    TypeDeclaration typeDeclaration = (TypeDeclaration) astRoot.types().get(0);
	    //typeDeclaration.bodyDeclarations().add(queryMethod);
	    
	    ListRewrite listRewrite = rewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
	    listRewrite.insertFirst(entityManagerDecl, null);
	    listRewrite.insertLast(factoryMethod, null);
	    
	    ICompilationUnit cu = (ICompilationUnit)astRoot.getJavaElement();
	    TextEdit edits;
		try {
			edits = rewriter.rewriteAST();
			Document document = new Document(cu.getSource());
			edits.apply(document);
			cu.getBuffer().setContents(document.get());
			cu.save(null, true);
			cu.commitWorkingCopy(true, null);
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void addExpressionAsArgumentToInvocation(CompilationUnit astRoot,MethodInvocation originalMI,MethodInvocation newMI,AssociationObject association,ASTRewrite rewriter) {
		Expression exp = originalMI.getExpression();
		String typeName = null;
		ITypeBinding typeBinding = originalMI.getExpression().resolveTypeBinding();
		if (typeBinding != null) {
		    typeName = typeBinding.getQualifiedName();
		    //System.out.println(typeName);
		}
		if(typeName.equals(association.getOwnedClass().getClassObject().getName())) {
			String getterMethodName = association.getOwnedClass().getIdFieldGetterName();
			MethodInvocation newExpression = astRoot.getAST().newMethodInvocation();
			newExpression.setExpression(astRoot.getAST().newSimpleName(exp.toString()));
			newExpression.setName(astRoot.getAST().newSimpleName(getterMethodName));

			ListRewrite listRewrite2 = rewriter.getListRewrite(newMI, MethodInvocation.ARGUMENTS_PROPERTY);
			listRewrite2.insertLast(newExpression, null);
		}else {
			ListRewrite listRewrite2 = rewriter.getListRewrite(newMI, MethodInvocation.ARGUMENTS_PROPERTY);
			listRewrite2.insertLast(originalMI.getExpression(), null);
		}
	}
	
	public ASTNode findFirstStatement(ASTNode node) {
		if(node instanceof Statement) {
			return node;
		}else {
			return findFirstStatement(node.getParent());
		}
	}
	
}
