package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.core.resources.IFile;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ASTReader;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ServiceInterfaceGenerator {
    private IJavaProject project;
    private SystemObject systemObject;

    public ServiceInterfaceGenerator(IJavaProject project, SystemObject systemObject) {
        this.project = project;
        this.systemObject = systemObject;
    }

    public Change createOrUpdateServiceInterface(String entityName, List<ServiceMethodRequirement> requirements, String servicePackage) throws JavaModelException {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceName = simpleEntityName + "Service";
        String serviceFileName = serviceName + ".java";

        IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
        if (packageFragment == null) {
            return null;
        }

        ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceFileName);
        if (existingCU.exists()) {
            return updateExistingServiceInterface(existingCU, entityName, requirements);
        }

        // Create the service interface content
        String serviceInterfaceContent = generateServiceInterfaceContent(entityName, requirements, servicePackage);

        // Create empty compilation unit first
        ICompilationUnit newCU = packageFragment.createCompilationUnit(serviceFileName, "", false, null);
        IFile file = (IFile) newCU.getResource();

        // Create a change that replaces the empty content with the interface content
        TextFileChange change = new TextFileChange("Create " + serviceName + " interface", file);
        change.setEdit(new ReplaceEdit(0, 0, serviceInterfaceContent));
        return change;
    }

    private Change updateExistingServiceInterface(ICompilationUnit existingCU, String entityName, List<ServiceMethodRequirement> requirements) throws JavaModelException, IllegalArgumentException {
        ASTParser parser = ASTParser.newParser(ASTReader.JLS);
        parser.setSource(existingCU);
        parser.setResolveBindings(true);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

        Set<String> existingMethodSignatures = extractExistingMethodSignatures(astRoot);
        List<ServiceMethodRequirement> missingMethods = findMissingMethods(requirements, existingMethodSignatures);
        if (missingMethods.isEmpty()) {
            return null;
        }

        ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
        AST ast = astRoot.getAST();
        TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        Set<String> neededImports = new TreeSet<>();
        for (ServiceMethodRequirement requirement : missingMethods) {
            if (requirement.getReturnType().contains("List")) {
                neededImports.add("java.util.List");
            }
            if (requirement.getMethodType() == ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING) {
                neededImports.add("java.util.Collection");
            }
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

        for (ServiceMethodRequirement requirement : missingMethods) {
            MethodDeclaration methodDecl = generateMethodDeclaration(ast, requirement);
            methodsRewrite.insertLast(methodDecl, new TextEditGroup("Add missing service method"));
        }

        IFile file = (IFile) existingCU.getResource();
        TextFileChange change = new TextFileChange("Update " + existingCU.getElementName(), file);
        change.setEdit(rewriter.rewriteAST());
        return change;
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

    private List<ServiceMethodRequirement> findMissingMethods(List<ServiceMethodRequirement> requirements, Set<String> existingSignatures) {
        List<ServiceMethodRequirement> missing = new ArrayList<>();
        for (ServiceMethodRequirement requirement : requirements) {
            if (!existingSignatures.contains(requirement.getMethodSignature())) {
                missing.add(requirement);
            }
        }
        return missing;
    }

    private String generateServiceInterfaceContent(String entityName, List<ServiceMethodRequirement> requirements, String packageName) {
        Set<String> imports = new TreeSet<>();

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");

        String entityPackage = UnmapJpaRelationshipsUtils.determineEntityPackage(systemObject, entityName);
        if (entityPackage != null && !entityPackage.equals(packageName)) {
            imports.add(entityName);
        }

        for (ServiceMethodRequirement requirement : requirements) {
            if (requirement.getReturnType().contains("List")) {
                imports.add("java.util.List");
            }
            if (requirement.getMethodType() == ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING) {
                imports.add("java.util.Collection");
            }
        }

        for (String imp : imports) {
            content.append("import ").append(imp).append(";\n");
        }

        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        content.append("\npublic interface ").append(simpleEntityName).append("Service {\n");

        for (ServiceMethodRequirement requirement : requirements) {
            content.append("    ").append(requirement.getMethodDeclaration()).append(";\n");
        }

        content.append("}\n");
        return content.toString();
    }

    private MethodDeclaration generateMethodDeclaration(AST ast, ServiceMethodRequirement requirement) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName(requirement.getMethodName()));

        Type returnType = createTypeFromString(ast, requirement.getReturnType());
        method.setReturnType2(returnType);

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        Type paramType = createTypeFromString(ast, requirement.getParameterTypeString());
        param.setType(paramType);
        param.setName(ast.newSimpleName(requirement.getParameterName()));
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