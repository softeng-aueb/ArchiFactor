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
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.aueb.java.archifactor.jpa.util.ServiceMethodUtils;
import gr.uom.java.ast.ASTReader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public abstract class BaseServiceImplementationGenerator {
	protected IJavaProject project;
	protected SystemObject systemObject;

	public BaseServiceImplementationGenerator(IJavaProject project, SystemObject systemObject) {
		this.project = project;
		this.systemObject = systemObject;
	}

	protected abstract List<String> getFrameworkImports();
	protected abstract List<String> getClassAnnotations();
	protected abstract String getEntityManagerFieldAnnotation();

	public void createOrUpdateServiceImplementation(
		String entityName, 
		List<ServiceMethodProvider> providers,
		String servicePackage,
		Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges,
		Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
	) throws JavaModelException {
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		String serviceImplName = simpleEntityName + "ServiceImpl";
		String serviceImplFileName = serviceImplName + ".java";

		IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
		if (packageFragment == null) {
			return;
		}

		ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceImplFileName);
		if (existingCU.exists()) {
			updateExistingServiceImplementation(existingCU, entityName, providers, compilationUnitChanges);
		} else {
			createNewServiceImplementation(packageFragment, entityName, providers, servicePackage, createCompilationUnitChanges);
		}
	}

	private void createNewServiceImplementation(
		IPackageFragment packageFragment, 
		String entityName, 
		List<ServiceMethodProvider> providers,
		String servicePackage, 
		Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
	) throws JavaModelException {
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		String serviceImplName = simpleEntityName + "ServiceImpl";
		String serviceImplFileName = serviceImplName + ".java";

		IContainer contextContainer = (IContainer) packageFragment.getResource();
		IFile serviceImplFile = null;
		if (contextContainer instanceof IProject) {
			IProject contextProject = (IProject) contextContainer;
			serviceImplFile = contextProject.getFile(serviceImplFileName);
		} else if (contextContainer instanceof IFolder) {
			IFolder contextFolder = (IFolder) contextContainer;
			serviceImplFile = contextFolder.getFile(serviceImplFileName);
		}

		ICompilationUnit serviceImplCompilationUnit = JavaCore.createCompilationUnitFrom(serviceImplFile);
		String serviceImplContent = generateServiceImplementationContent(entityName, providers, servicePackage);
		Document document = new Document(serviceImplContent);

		try {
			CreateCompilationUnitChange createChange = new CreateCompilationUnitChange(serviceImplCompilationUnit, document.get(), serviceImplFile.getCharset());
			createCompilationUnitChanges.put(serviceImplCompilationUnit, createChange);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void updateExistingServiceImplementation(
		ICompilationUnit existingCU, 
		String entityName, 
		List<ServiceMethodProvider> providers,
		Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges
	) throws JavaModelException, IllegalArgumentException {
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setSource(existingCU);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		Set<String> existingMethodSignatures = ServiceMethodUtils.extractExistingMethodSignatures(astRoot);
		List<ServiceMethodProvider> missingMethods = ServiceMethodUtils.findMissingMethods(providers, existingMethodSignatures);
		if (missingMethods.isEmpty()) {
			return;
		}

		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
		ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

		Set<String> neededImports = new TreeSet<>();
		for (ServiceMethodProvider provider : missingMethods) {
			neededImports.addAll(provider.getRequiredImports());
		}

		ListRewrite importsRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
		Set<String> existingImports = ServiceMethodUtils.extractExistingImports(astRoot);
		for (String importName : neededImports) {
			if (!existingImports.contains(importName)) {
				ImportDeclaration importDecl = ast.newImportDeclaration();
				importDecl.setName(ast.newName(importName));
				importsRewrite.insertLast(importDecl, new TextEditGroup("Add missing import"));
			}
		}

		for (ServiceMethodProvider provider : missingMethods) {
			MethodDeclaration methodDecl = generateMethodImplementation(ast, provider);
			methodsRewrite.insertLast(methodDecl, new TextEditGroup("Add missing service method implementation"));
		}

		CompilationUnitChange change = new CompilationUnitChange("Update " + existingCU.getElementName(), existingCU);
		change.setEdit(rewriter.rewriteAST());
		compilationUnitChanges.put(existingCU, change);
	}

	protected String generateServiceImplementationContent(String entityName, List<ServiceMethodProvider> providers, String packageName) {
		Set<String> imports = new TreeSet<>();

		StringBuilder content = new StringBuilder();
		content.append("package ").append(packageName).append(";\n\n");

		String entityPackage = UnmapJpaRelationshipsUtils.determineEntityPackage(systemObject, entityName);
		if (entityPackage != null && !entityPackage.equals(packageName)) {
			imports.add(entityName);
		}

		imports.addAll(getFrameworkImports());
		imports.add("jakarta.persistence.EntityManager");

		for (ServiceMethodProvider provider : providers) {
			imports.addAll(provider.getRequiredImports());
		}

		for (String imp : imports) {
			content.append("import ").append(imp).append(";\n");
		}

		content.append("\n");
		for (String annotation : getClassAnnotations()) {
			content.append(annotation).append("\n");
		}
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		content.append("public class ").append(simpleEntityName).append("ServiceImpl implements ").append(simpleEntityName).append("Service {\n");
		content.append("    ").append(getEntityManagerFieldAnnotation()).append("\n");
		content.append("    private EntityManager entityManager;\n\n");

		for (ServiceMethodProvider provider : providers) {
			content.append(provider.generateMethodImplementationString());
			content.append("\n");
		}

		content.append("}\n");
		return content.toString();
	}

	private MethodDeclaration generateMethodImplementation(AST ast, ServiceMethodProvider provider) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(provider.getMethodName()));

		MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
		overrideAnnotation.setTypeName(ast.newName("Override"));
		method.modifiers().add(overrideAnnotation);
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

		Type returnType = ServiceMethodUtils.createTypeFromString(ast, provider.getReturnType());
		method.setReturnType2(returnType);

		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		Type paramType = ServiceMethodUtils.createTypeFromString(ast, provider.getParameterTypeString());
		param.setType(paramType);
		param.setName(ast.newSimpleName(provider.getParameterName()));
		method.parameters().add(param);

		Block body = provider.createMethodBody(ast);
		method.setBody(body);
		return method;
	}
}
