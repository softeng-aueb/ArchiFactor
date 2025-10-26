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
import gr.uom.java.ast.ASTReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ServiceInterfaceGenerator {
    private IJavaProject project;
    private SystemObject systemObject;

    public ServiceInterfaceGenerator(IJavaProject project, SystemObject systemObject) {
        this.project = project;
        this.systemObject = systemObject;
    }

    public void createOrUpdateServiceInterface(
		String entityName, 
		List<ServiceMethodProvider> providers, 
		String servicePackage,
        Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges,
        Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
    ) throws JavaModelException {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceName = simpleEntityName + "Service";
        String serviceFileName = serviceName + ".java";

        IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
        if (packageFragment == null) {
            return;
        }

        ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceFileName);
        if (existingCU.exists()) {
            updateExistingServiceInterface(existingCU, entityName, providers, compilationUnitChanges);
        } else {
            createNewServiceInterface(packageFragment, entityName, providers, servicePackage, createCompilationUnitChanges);
        }
    }

    private void createNewServiceInterface(
		IPackageFragment packageFragment, 
		String entityName, 
		List<ServiceMethodProvider> providers,
        String servicePackage, 
        Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges
    ) throws JavaModelException {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceName = simpleEntityName + "Service";
        String serviceFileName = serviceName + ".java";

        IContainer contextContainer = (IContainer) packageFragment.getResource();
        IFile serviceFile = null;
        if (contextContainer instanceof IProject) {
            IProject contextProject = (IProject) contextContainer;
            serviceFile = contextProject.getFile(serviceFileName);
        } else if (contextContainer instanceof IFolder) {
            IFolder contextFolder = (IFolder) contextContainer;
            serviceFile = contextFolder.getFile(serviceFileName);
        }

        ICompilationUnit serviceCompilationUnit = JavaCore.createCompilationUnitFrom(serviceFile);
        String serviceInterfaceContent = generateServiceInterfaceContent(entityName, providers, servicePackage);
        Document document = new Document(serviceInterfaceContent);

        try {
            CreateCompilationUnitChange createChange = new CreateCompilationUnitChange(serviceCompilationUnit, document.get(), serviceFile.getCharset());
            createCompilationUnitChanges.put(serviceCompilationUnit, createChange);
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    private void updateExistingServiceInterface(
		ICompilationUnit existingCU, 
		String entityName, 
		List<ServiceMethodProvider> providers,
        Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges
    ) throws JavaModelException, IllegalArgumentException {
        ASTParser parser = ASTParser.newParser(ASTReader.JLS);
        parser.setSource(existingCU);
        parser.setResolveBindings(true);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

        Set<String> existingMethodSignatures = extractExistingMethodSignatures(astRoot);
        List<ServiceMethodProvider> missingMethods = findMissingMethods(providers, existingMethodSignatures);
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
        Set<String> existingImports = extractExistingImports(astRoot);
        for (String importName : neededImports) {
            if (!existingImports.contains(importName)) {
                ImportDeclaration importDecl = ast.newImportDeclaration();
                importDecl.setName(ast.newName(importName));
                importsRewrite.insertLast(importDecl, new TextEditGroup("Add missing import"));
            }
        }

        for (ServiceMethodProvider provider : missingMethods) {
            MethodDeclaration methodDecl = generateMethodDeclaration(ast, provider);
            methodsRewrite.insertLast(methodDecl, new TextEditGroup("Add missing service method"));
        }

        CompilationUnitChange change = new CompilationUnitChange("Update " + existingCU.getElementName(), existingCU);
        change.setEdit(rewriter.rewriteAST());
        compilationUnitChanges.put(existingCU, change);
    }

    private Set<String> extractExistingImports(CompilationUnit astRoot) {
        Set<String> imports = new HashSet<>();
        for (Object obj : astRoot.imports()) {
            if (obj instanceof ImportDeclaration) {
                ImportDeclaration importDecl = (ImportDeclaration) obj;
                imports.add(importDecl.getName().getFullyQualifiedName());
            }
        }
        return imports;
    }

    private Set<String> extractExistingMethodSignatures(CompilationUnit astRoot) {
        Set<String> signatures = new HashSet<>();
        if (astRoot.types().isEmpty()) {
            return signatures;
        }

        Object firstType = astRoot.types().get(0);
        if (!(firstType instanceof TypeDeclaration)) {
            return signatures;
        }

        TypeDeclaration typeDecl = (TypeDeclaration) firstType;
        for (MethodDeclaration method : typeDecl.getMethods()) {
            String signature = buildMethodSignature(method);
            signatures.add(signature);
        }
        return signatures;
    }

    private String buildMethodSignature(MethodDeclaration method) {
        String methodName = method.getName().getIdentifier();
        if (method.parameters().isEmpty()) {
            return methodName + "()";
        }

        SingleVariableDeclaration param = (SingleVariableDeclaration) method.parameters().get(0);
        String paramType = param.getType().toString();
        String paramName = param.getName().getIdentifier();
        return methodName + "(" + paramType + " " + paramName + ")";
    }

    private List<ServiceMethodProvider> findMissingMethods(List<ServiceMethodProvider> providers, Set<String> existingSignatures) {
        List<ServiceMethodProvider> missing = new ArrayList<>();
        for (ServiceMethodProvider provider : providers) {
            if (!existingSignatures.contains(provider.getMethodSignature())) {
                missing.add(provider);
            }
        }
        return missing;
    }

    private String generateServiceInterfaceContent(String entityName, List<ServiceMethodProvider> providers, String packageName) {
        Set<String> imports = new TreeSet<>();

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");

        String entityPackage = UnmapJpaRelationshipsUtils.determineEntityPackage(systemObject, entityName);
        if (entityPackage != null && !entityPackage.equals(packageName)) {
            imports.add(entityName);
        }

        for (ServiceMethodProvider provider : providers) {
            imports.addAll(provider.getRequiredImports());
        }

        for (String imp : imports) {
            content.append("import ").append(imp).append(";\n");
        }

        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        content.append("\npublic interface ").append(simpleEntityName).append("Service {\n");

        for (ServiceMethodProvider provider : providers) {
            content.append("    ").append(provider.getMethodDeclaration()).append(";\n");
        }

        content.append("}\n");
        return content.toString();
    }

    private MethodDeclaration generateMethodDeclaration(AST ast, ServiceMethodProvider provider) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName(provider.getMethodName()));

        Type returnType = createTypeFromString(ast, provider.getReturnType());
        method.setReturnType2(returnType);

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        Type paramType = createTypeFromString(ast, provider.getParameterTypeString());
        param.setType(paramType);
        param.setName(ast.newSimpleName(provider.getParameterName()));
        method.parameters().add(param);
        return method;
    }

    private Type createTypeFromString(AST ast, String typeString) {
        if (typeString.contains("<") && typeString.contains(">")) {
            String[] parts = typeString.split("[<>]");
            String containerType = parts[0];
            String elementType = parts[1];
            ParameterizedType paramType = ast.newParameterizedType(ast.newSimpleType(ast.newName(containerType)));
            paramType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));
            return paramType;
        }
        return ast.newSimpleType(ast.newName(typeString));
    }
}