package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.core.resources.IFile;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;
import gr.aueb.java.jpa.JpaModel;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ListIterator;

public class ServiceFactoryGenerator {
    private IJavaProject project;
    private SystemObject systemObject;
    private Map<String, ClassObject> entityMap;
    
    public ServiceFactoryGenerator(IJavaProject project, SystemObject systemObject) {
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
    
    public Change createOrUpdateServiceFactory(Set<String> entityNames, String servicePackage) throws JavaModelException {
        String factoryName = "ServiceFactory";
        String factoryFileName = factoryName + ".java";

        IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
        if (packageFragment == null) {
            return null;
        }

        ICompilationUnit existingCU = packageFragment.getCompilationUnit(factoryFileName);
        if (existingCU.exists()) {
            return updateExistingServiceFactory(existingCU, entityNames);
        } else {
            return createNewServiceFactory(packageFragment, entityNames, servicePackage);
        }
    }
    
    private Change createNewServiceFactory(IPackageFragment packageFragment, Set<String> entityNames, String servicePackage) throws JavaModelException {
        String factoryName = "ServiceFactory";
        String factoryFileName = factoryName + ".java";
        
        // Create the service factory content
        String factoryContent = generateServiceFactoryContent(entityNames, servicePackage);
        
        // Create empty compilation unit first
        ICompilationUnit newCU = packageFragment.createCompilationUnit(factoryFileName, "", false, null);
        IFile file = (IFile) newCU.getResource();
        
        // Create a change that replaces the empty content with the service factory content
        TextFileChange change = new TextFileChange("Create " + factoryName, file);
        change.setEdit(new ReplaceEdit(0, 0, factoryContent));
        return change;
    }

    private String generateServiceFactoryContent(Set<String> entityNames, String packageName) {
        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");
        content.append("import jakarta.enterprise.inject.spi.CDI;\n");
        for (String entityName : entityNames) {
            String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
            String serviceInterface = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)) + "." + simpleEntityName + "Service";
            content.append("import ").append(serviceInterface).append(";\n");
        }
        content.append("\n");
        content.append("public class ServiceFactory {\n\n");
        for (String entityName : entityNames) {
            String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
            String serviceName = simpleEntityName + "Service";
            String methodName = "get" + serviceName;
            content.append("    public static ").append(serviceName).append(" ").append(methodName).append("() {\n");
            content.append("        return CDI.current().select(").append(serviceName).append(".class).get();\n");
            content.append("    }\n\n");
        }
        content.append("}\n");
        return content.toString();
    }

    private Change updateExistingServiceFactory(ICompilationUnit existingCU, Set<String> entityNames) throws JavaModelException {
        IFile file = (IFile) existingCU.getResource();

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
            return null;
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
            return null;
        }

        TextFileChange change = new TextFileChange("Update " + existingCU.getElementName(), file);
        change.setEdit(rewriter.rewriteAST());
        return change;
    }

    private TypeDeclaration findServiceFactoryClass(CompilationUnit astRoot) {
        for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
            if ("ServiceFactory".equals(type.getName().getIdentifier())) {
                return type;
            }
        }
        return null;
    }

    private boolean hasServiceGetterMethod(TypeDeclaration factoryClass, String entityName) {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String expectedMethodName = "get" + simpleEntityName + "Service";
        for (MethodDeclaration method : factoryClass.getMethods()) {
            if (expectedMethodName.equals(method.getName().getIdentifier())) {
                return true;
            }
        }
        return false;
    }

    private MethodDeclaration createServiceGetterMethod(AST ast, String entityName) {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceName = simpleEntityName + "Service";
        String methodName = "get" + serviceName;
        
        MethodDeclaration method = createMethodDeclaration(ast, methodName, serviceName);
        Block methodBody = createMethodBody(ast, serviceName);
        method.setBody(methodBody);
        return method;
    }
    
    private MethodDeclaration createMethodDeclaration(AST ast, String methodName, String returnTypeName) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName(methodName));
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        method.setReturnType2(ast.newSimpleType(ast.newName(returnTypeName)));
        return method;
    }
    
    private Block createMethodBody(AST ast, String serviceName) {
        Block methodBody = ast.newBlock();
        ReturnStatement returnStatement = createReturnStatement(ast, serviceName);
        methodBody.statements().add(returnStatement);
        return methodBody;
    }
    
    private ReturnStatement createReturnStatement(AST ast, String serviceName) {
        ReturnStatement returnStatement = ast.newReturnStatement();
        MethodInvocation cdiSelectGetCall = createCdiSelectGetCall(ast, serviceName);
        returnStatement.setExpression(cdiSelectGetCall);
        return returnStatement;
    }
    
    private MethodInvocation createCdiSelectGetCall(AST ast, String serviceName) {
        MethodInvocation currentCall = createCdiCurrentCall(ast);
        MethodInvocation selectCall = createSelectCall(ast, currentCall, serviceName);
        
        MethodInvocation getCall = ast.newMethodInvocation();
        getCall.setName(ast.newSimpleName("get"));
        getCall.setExpression(selectCall);
        
        return getCall;
    }
    
    private MethodInvocation createCdiCurrentCall(AST ast) {
        MethodInvocation currentCall = ast.newMethodInvocation();
        currentCall.setExpression(ast.newName("CDI"));
        currentCall.setName(ast.newSimpleName("current"));
        return currentCall;
    }
    
    private MethodInvocation createSelectCall(AST ast, MethodInvocation currentCall, String serviceName) {
        MethodInvocation selectCall = ast.newMethodInvocation();
        selectCall.setName(ast.newSimpleName("select"));
        selectCall.setExpression(currentCall);
        
        TypeLiteral classLiteral = ast.newTypeLiteral();
        classLiteral.setType(ast.newSimpleType(ast.newName(serviceName)));
        selectCall.arguments().add(classLiteral);
        return selectCall;
    }
}