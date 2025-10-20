package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.aueb.java.jpa.JpaModel;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ListIterator;

public abstract class BaseServiceFactoryGenerator {
	protected IJavaProject project;
	protected SystemObject systemObject;
	protected Map<String, ClassObject> entityMap;

	public BaseServiceFactoryGenerator(IJavaProject project, SystemObject systemObject) {
		this.project = project;
		this.systemObject = systemObject;
		this.entityMap = new HashMap<>();

		buildEntityMap();
	}

	private void buildEntityMap() {
		ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
		while (classIterator.hasNext()) {
			ClassObject classObj = classIterator.next();
			if (JpaModel.isEntity(classObj)) {
				entityMap.put(classObj.getName(), classObj);
			}
		}
	}

	public void createOrUpdateServiceFactory(
		Set<String> entityNames, 
		String servicePackage,
		Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges,
		Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
	) throws JavaModelException {
		String factoryName = "ServiceFactory";
		String factoryFileName = factoryName + ".java";

		IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
		if (packageFragment == null) {
			return;
		}

		ICompilationUnit existingCU = packageFragment.getCompilationUnit(factoryFileName);
		if (existingCU.exists()) {
			updateExistingServiceFactory(existingCU, entityNames, compilationUnitChanges);
		} else {
			createNewServiceFactory(packageFragment, entityNames, servicePackage, createCompilationUnitChanges);
		}
	}

	protected void createNewServiceFactory(
		IPackageFragment packageFragment, 
		Set<String> entityNames, 
		String servicePackage,
		Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
	) throws JavaModelException {
		String factoryName = "ServiceFactory";
		String factoryFileName = factoryName + ".java";

		IContainer contextContainer = (IContainer) packageFragment.getResource();
		IFile factoryFile = null;
		if (contextContainer instanceof IProject) {
			IProject contextProject = (IProject) contextContainer;
			factoryFile = contextProject.getFile(factoryFileName);
		} else if (contextContainer instanceof IFolder) {
			IFolder contextFolder = (IFolder) contextContainer;
			factoryFile = contextFolder.getFile(factoryFileName);
		}

		ICompilationUnit factoryCompilationUnit = JavaCore.createCompilationUnitFrom(factoryFile);
		String factoryContent = generateServiceFactoryContent(entityNames, servicePackage);
		Document document = new Document(factoryContent);

		try {
			CreateCompilationUnitChange createChange = new CreateCompilationUnitChange(factoryCompilationUnit, document.get(), factoryFile.getCharset());
			createCompilationUnitChanges.put(factoryCompilationUnit, createChange);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	protected abstract String generateServiceFactoryContent(Set<String> entityNames, String packageName);

	protected void updateExistingServiceFactory(
		ICompilationUnit existingCU, 
		Set<String> entityNames,
		Map<ICompilationUnit, 
		CompilationUnitChange> compilationUnitChanges
	) throws JavaModelException {
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setSource(existingCU);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();

		boolean hasChanges = false;
		TextEditGroup editGroup = new TextEditGroup("Update ServiceFactory");

		TypeDeclaration factoryClass = findServiceFactoryClass(astRoot);
		if (factoryClass == null) {
			return;
		}

		for (String entityName : entityNames) {
			if (!hasServiceGetterMethod(factoryClass, entityName)) {
				String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
				String serviceInterface = simpleEntityName + "Service";
				String servicePackage = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName));
				String fullServiceClass = servicePackage + "." + serviceInterface;

				boolean importExists = false;
				List<ImportDeclaration> imports = astRoot.imports();
				for (ImportDeclaration imp : imports) {
					if (imp.getName().getFullyQualifiedName().equals(fullServiceClass)) {
						importExists = true;
						break;
					}
				}

				if (!importExists) {
					ImportDeclaration serviceImport = ast.newImportDeclaration();
					serviceImport.setName(ast.newName(fullServiceClass));
					ListRewrite importsRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
					importsRewrite.insertLast(serviceImport, editGroup);
				}

				MethodDeclaration getterMethod = createServiceGetterMethod(ast, entityName);

				ListRewrite methodsRewrite = rewriter.getListRewrite(factoryClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				methodsRewrite.insertLast(getterMethod, editGroup);
				hasChanges = true;
			}
		}

		if (!hasChanges) {
			return;
		}

		CompilationUnitChange change = new CompilationUnitChange("Update " + existingCU.getElementName(), existingCU);
		change.setEdit(rewriter.rewriteAST());
		compilationUnitChanges.put(existingCU, change);
	}

	protected TypeDeclaration findServiceFactoryClass(CompilationUnit astRoot) {
		for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
			if ("ServiceFactory".equals(type.getName().getIdentifier())) {
				return type;
			}
		}
		return null;
	}

	protected boolean hasServiceGetterMethod(TypeDeclaration factoryClass, String entityName) {
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		String expectedMethodName = "get" + simpleEntityName + "Service";
		for (MethodDeclaration method : factoryClass.getMethods()) {
			if (expectedMethodName.equals(method.getName().getIdentifier())) {
				return true;
			}
		}
		return false;
	}

	protected abstract MethodDeclaration createServiceGetterMethod(AST ast, String entityName);
}
