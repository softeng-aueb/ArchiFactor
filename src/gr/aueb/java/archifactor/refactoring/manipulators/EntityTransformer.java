package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.core.resources.IFile;

import gr.uom.java.ast.SystemObject;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;

import java.util.List;
import java.util.Iterator;

public class EntityTransformer {
    private SystemObject systemObject;

    public EntityTransformer(SystemObject systemObject) {
        this.systemObject = systemObject;
    }

    public Change transformFromEntity(ClassObject entity, RelationshipInfo relationship) throws Exception {
        ICompilationUnit cu = (ICompilationUnit) entity.getITypeRoot();
        if (cu == null) {
            return null;
        }

        ASTParser parser = ASTParser.newParser(ASTReader.JLS);
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
        ImportRewrite importRewrite = ImportRewrite.create(astRoot, true);
        
        boolean hasChanges = false;

        // 1. Transform field declaration
        FieldDeclaration fieldDecl = findFieldDeclaration(astRoot, relationship.getFieldName());
        if (fieldDecl != null) {
            System.out.println("[DEBUG] Found field declaration for: " + relationship.getFieldName() + " in " + entity.getName());
            hasChanges = transformFieldDeclaration(fieldDecl, relationship, rewriter, importRewrite, astRoot.getAST(), entity);
        }

        // 2. Transform methods in the SAME AST operation
        hasChanges |= transformMethodsInSameAST(astRoot, entity, relationship, relationship.getFieldName(), rewriter, importRewrite);

        if (!hasChanges) {
            return null;
        }

        IFile file = entity.getIFile();
        TextFileChange change = new TextFileChange(cu.getElementName(), file);
        change.setEdit(rewriter.rewriteAST());
        if (importRewrite.hasRecordedChanges()) {
            change.addEdit(importRewrite.rewriteImports(null));
        }
        return change;
    }
    
    private FieldDeclaration findFieldDeclaration(CompilationUnit astRoot, String fieldName) {
        for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
            for (FieldDeclaration field : type.getFields()) {
                for (Object fragment : field.fragments()) {
                    if (fragment instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                        if (fieldName.equals(vdf.getName().getIdentifier())) {
                            return field;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private boolean transformFieldDeclaration(
        FieldDeclaration fieldDecl, 
        RelationshipInfo relationship, 
        ASTRewrite rewriter, 
        ImportRewrite importRewrite,
        AST ast,
        ClassObject entity
    ) {
        TextEditGroup editGroup = new TextEditGroup("Transform JPA field");

        // Get the field modifiers list to make the necessary changes
        ListRewrite modifiersRewrite = rewriter.getListRewrite(fieldDecl, FieldDeclaration.MODIFIERS2_PROPERTY);

        // Remove JPA relationship annotations
        boolean removedAnnotations = false;
        for (IExtendedModifier modifier : (List<IExtendedModifier>) fieldDecl.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation annotation = (Annotation) modifier;
                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                if (UnmapJpaRelationshipsUtils.isJpaRelationshipAnnotation(annotationName) || UnmapJpaRelationshipsUtils.isJoinAnnotation(annotationName)) {
                    modifiersRewrite.remove(annotation, editGroup);
                    removedAnnotations = true;
                }
            }
        }

        // Add the @Transient annotation to the transformed field
        boolean hasChanges = false;
        if (removedAnnotations) {
            importRewrite.addImport("jakarta.persistence.Transient");

            MarkerAnnotation transientAnnotation = ast.newMarkerAnnotation();
            transientAnnotation.setTypeName(ast.newName("Transient"));
            modifiersRewrite.insertFirst(transientAnnotation, editGroup);
            hasChanges = true;
        }

        if (relationship.isOwningSide()) {
            System.out.println("[DEBUG] Adding FK field for single reference: " + entity.getName() + "." + relationship.getFieldName());
            hasChanges |= addForeignKeyField(fieldDecl, relationship, rewriter, importRewrite, ast, editGroup);
        }
        return hasChanges;
    }

    private boolean addForeignKeyField(
        FieldDeclaration originalField, 
        RelationshipInfo relationship, 
        ASTRewrite rewriter, 
        ImportRewrite importRewrite,
        AST ast,
        TextEditGroup editGroup
    ) {
        String fkFieldName = relationship.getJoinColumnName();
        String fkFieldType = relationship.getReferencedPkType();
        if (fkFieldType.contains(".")) {
            fkFieldType = fkFieldType.substring(fkFieldType.lastIndexOf(".") + 1);
        }

        System.out.println("[DEBUG] FK field - name: " + fkFieldName + ", type: " + fkFieldType);
        
        // Create the foreign key field declaration
        VariableDeclarationFragment fkFragment = ast.newVariableDeclarationFragment();
        fkFragment.setName(ast.newSimpleName(fkFieldName));
        FieldDeclaration fkField = ast.newFieldDeclaration(fkFragment);
        fkField.setType(UnmapJpaRelationshipsUtils.createAstTypeForFieldType(ast, fkFieldType));

        // Add @Column annotation FIRST (so it appears above the field)
        NormalAnnotation columnAnnotation = ast.newNormalAnnotation();
        columnAnnotation.setTypeName(ast.newName("Column"));
        MemberValuePair nameValue = ast.newMemberValuePair();
        nameValue.setName(ast.newSimpleName("name"));
        StringLiteral columnName = ast.newStringLiteral();
        columnName.setLiteralValue(fkFieldName);
        nameValue.setValue(columnName);
        columnAnnotation.values().add(nameValue);
        
        fkField.modifiers().add(columnAnnotation);
        fkField.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

        // Add imports
        importRewrite.addImport("jakarta.persistence.Column");
        if (relationship.getReferencedPkType().contains(".") && !relationship.getReferencedPkType().contains("lang")) {
            importRewrite.addImport(relationship.getReferencedPkType());
        }

        // Insert the field after the original field
        TypeDeclaration parentType = (TypeDeclaration) originalField.getParent();
        ListRewrite fieldsRewrite = rewriter.getListRewrite(parentType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        fieldsRewrite.insertAfter(fkField, originalField, editGroup);
        return true;
    }

    private boolean transformMethodsInSameAST(
        CompilationUnit astRoot, 
        ClassObject entity, 
        RelationshipInfo relationship, 
        String targetFieldName, 
        ASTRewrite rewriter, 
        ImportRewrite importRewrite
    ) {
        System.out.println("[DEBUG] Transforming methods for field: " + targetFieldName + " in " + entity.getName());

        boolean hasChanges = false;
        AST ast = astRoot.getAST();

        // 1. Find or create direct field access method
        MethodDeclaration directAccessMethod = findDirectFieldAccessMethod(astRoot, targetFieldName);
        String directAccessMethodName;
        if (directAccessMethod == null) {
            System.out.println("[DEBUG] No direct field access method found, creating one");
            directAccessMethod = createDirectFieldAccessMethod(astRoot, targetFieldName, entity, relationship, rewriter, ast);
            directAccessMethodName = directAccessMethod.getName().getIdentifier();
            hasChanges = true;
        } else {
            directAccessMethodName = directAccessMethod.getName().getIdentifier();
            System.out.println("[DEBUG] Found direct field access method: " + directAccessMethodName);
        }

        // 2. Transform the direct access method with lazy loading
        hasChanges |= transformDirectAccessMethodInAST(directAccessMethod, entity, relationship, targetFieldName, rewriter, importRewrite, ast);

        // 3. Replace all field accesses with method calls (except assignments)
        hasChanges |= replaceFieldAccessesWithMethodCalls(astRoot, targetFieldName, directAccessMethodName, rewriter, ast);

        // 4. Add FK field updates after ALL assignments to owning side fields
        if (relationship.isOwningSide()) {
            System.out.println("[DEBUG] Adding FK updates for all assignments to owning side field: " + targetFieldName);
            hasChanges |= addFKUpdatesForAllAssignments(astRoot, targetFieldName, relationship, rewriter, ast);
        }

        return hasChanges;
    }

    private MethodDeclaration findDirectFieldAccessMethod(CompilationUnit astRoot, String fieldName) {
        for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
            for (MethodDeclaration method : type.getMethods()) {
                if (returnsFieldDirectly(method, fieldName)) {
                    return method;
                }
            }
        }
        return null;
    }

    private boolean returnsFieldDirectly(MethodDeclaration method, String fieldName) {
        Block body = method.getBody();
        if (body == null) return false;
        
        // Look for: return fieldName; or return this.fieldName;
        for (Statement stmt : (List<Statement>) body.statements()) {
            if (stmt instanceof ReturnStatement) {
                ReturnStatement returnStmt = (ReturnStatement) stmt;
                Expression expr = returnStmt.getExpression();
                if (expr instanceof SimpleName && fieldName.equals(((SimpleName) expr).getIdentifier())) {
                    return true; // return fieldName;
                } else if (expr instanceof FieldAccess) {
                    FieldAccess fa = (FieldAccess) expr;
                    if (fieldName.equals(fa.getName().getIdentifier())) {
                        return true; // return this.fieldName;
                    }
                }
            }
        }
        return false;
    }

    private MethodDeclaration createDirectFieldAccessMethod(
        CompilationUnit astRoot, 
        String fieldName, 
        ClassObject entity,
        RelationshipInfo relationship,
        ASTRewrite rewriter, 
        AST ast
    ) {
        MethodDeclaration method = ast.newMethodDeclaration();
        String methodName = "get" + UnmapJpaRelationshipsUtils.capitalize(fieldName); // Lombok compliant name
        method.setName(ast.newSimpleName(methodName));

        // Add public modifier for lombok compliance
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

        FieldObject fieldObj = getFieldFromEntity(fieldName, entity);
        String fieldType = fieldObj.getType().getClassType();
        String genericType = fieldObj.getType().getGenericType();

        Type returnType;
        if (genericType != null && !genericType.isEmpty()) {
            String collectionType = UnmapJpaRelationshipsUtils.getSimpleClassName(fieldType);
            String elementType = UnmapJpaRelationshipsUtils.getSimpleClassName(genericType.replaceAll("[<>]", "").trim());

            ParameterizedType parameterizedType = ast.newParameterizedType(ast.newSimpleType(ast.newName(collectionType)));
            parameterizedType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));
            returnType = parameterizedType;
        } else {
            String simpleType = UnmapJpaRelationshipsUtils.getSimpleClassName(fieldType);
            returnType = ast.newSimpleType(ast.newName(simpleType));
        }
        method.setReturnType2(returnType);

        Block body = ast.newBlock();
        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName(fieldName));
        body.statements().add(returnStmt);
        method.setBody(body);

        // Insert method into class
        TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        methodsRewrite.insertLast(method, new TextEditGroup("Add direct field access method"));
        System.out.println("[DEBUG] Created direct field access method: " + methodName + "() returning " + returnType);
        return method;
    }

    private FieldObject getFieldFromEntity(String fieldName, ClassObject entity) {
        Iterator<FieldObject> fieldIterator = entity.getFieldIterator();
        while (fieldIterator.hasNext()) {
            FieldObject field = fieldIterator.next();
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private boolean transformDirectAccessMethodInAST(
        MethodDeclaration directAccessMethod, 
        ClassObject entity, 
        RelationshipInfo relationship, 
        String fieldName, 
        ASTRewrite rewriter, 
        ImportRewrite importRewrite, 
        AST ast
    ) {
        System.out.println("[DEBUG] Transforming direct access method: " + directAccessMethod.getName().getIdentifier());

        // Create new method body with lazy loading
        FieldObject fieldObj = getFieldFromEntity(fieldName, entity);
        String genericType = fieldObj.getType().getGenericType();

        Block newMethodBody;
        if (genericType != null && !genericType.isEmpty()) {
            newMethodBody = createCollectionLazyLoadingBlock(ast, fieldName, entity, relationship, importRewrite);
        } else {
            newMethodBody = createSimpleTypeLazyLoadingBlock(ast, fieldName, entity, relationship, importRewrite);
        }

        // Replace the method body
        Block methodBody = directAccessMethod.getBody();
        rewriter.replace(methodBody, newMethodBody, new TextEditGroup("Transform direct access method"));
        return true;
    }

    private Block createCollectionLazyLoadingBlock(AST ast, String fieldName, ClassObject entity, RelationshipInfo relationship, ImportRewrite importRewrite) {
        Block newBody = ast.newBlock();

        String targetEntityName = relationship.getToEntity();
        String simpleTargetEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(targetEntityName);
        String serviceName = UnmapJpaRelationshipsUtils.decapitalize(simpleTargetEntityName) + "Service";
        String serviceClassName = simpleTargetEntityName + "Service";

        MethodInvocation isEmptyCall = ast.newMethodInvocation();
        isEmptyCall.setExpression(ast.newSimpleName(fieldName));
        isEmptyCall.setName(ast.newSimpleName("isEmpty"));

        IfStatement lazyLoadIf = ast.newIfStatement();
        lazyLoadIf.setExpression(isEmptyCall);

        VariableDeclarationFragment serviceVarFragment = ast.newVariableDeclarationFragment();
        serviceVarFragment.setName(ast.newSimpleName(serviceName));

        MethodInvocation getServiceCall = ast.newMethodInvocation();
        getServiceCall.setExpression(ast.newName("ServiceFactory"));
        getServiceCall.setName(ast.newSimpleName("get" + serviceClassName));
        serviceVarFragment.setInitializer(getServiceCall);

        VariableDeclarationStatement serviceVarDecl = ast.newVariableDeclarationStatement(serviceVarFragment);
        serviceVarDecl.setType(ast.newSimpleType(ast.newName(serviceClassName)));

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(serviceVarDecl);

        MethodInvocation addAllCall = ast.newMethodInvocation();
        addAllCall.setExpression(ast.newSimpleName(fieldName));
        addAllCall.setName(ast.newSimpleName("addAll"));

        MethodInvocation serviceMethodCall = ast.newMethodInvocation();
        serviceMethodCall.setExpression(ast.newSimpleName(serviceName));
        serviceMethodCall.setName(ast.newSimpleName("get" + simpleTargetEntityName + "sByForeignKey"));

        String fkColumnName = relationship.getJoinColumnName();
        StringLiteral columnNameLiteral = ast.newStringLiteral();
        columnNameLiteral.setLiteralValue(fkColumnName);
        serviceMethodCall.arguments().add(columnNameLiteral);

        JpaAnnotationExtractor jpaAnnotationExtractor = new JpaAnnotationExtractor(systemObject);
        String idAnnotatedFieldName = jpaAnnotationExtractor.extractIdFieldName(entity.getName());
        serviceMethodCall.arguments().add(ast.newSimpleName(idAnnotatedFieldName));

        addAllCall.arguments().add(serviceMethodCall);
        thenBlock.statements().add(ast.newExpressionStatement(addAllCall));
        lazyLoadIf.setThenStatement(thenBlock);
        newBody.statements().add(lazyLoadIf);

        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(ast.newSimpleName(fieldName));
        newBody.statements().add(returnStatement);

        String servicePackage = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entity);
        importRewrite.addImport(servicePackage + "." + serviceClassName);
        importRewrite.addImport(servicePackage + ".ServiceFactory");

        return newBody;
    }

    private Block createSimpleTypeLazyLoadingBlock(AST ast, String fieldName, ClassObject entity, RelationshipInfo relationship, ImportRewrite importRewrite) {
        Block newBody = ast.newBlock();

        String targetEntityName = relationship.getToEntity();
        String simpleTargetEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(targetEntityName);
        String serviceName = UnmapJpaRelationshipsUtils.decapitalize(simpleTargetEntityName) + "Service";
        String serviceClassName = simpleTargetEntityName + "Service";

        InfixExpression fieldNullCheck = ast.newInfixExpression();
        fieldNullCheck.setLeftOperand(ast.newSimpleName(fieldName));
        fieldNullCheck.setOperator(InfixExpression.Operator.EQUALS);
        fieldNullCheck.setRightOperand(ast.newNullLiteral());

        String fkFieldName = relationship.getJoinColumnName();
        InfixExpression fkNotNullCheck = ast.newInfixExpression();
        fkNotNullCheck.setLeftOperand(ast.newSimpleName(fkFieldName));
        fkNotNullCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
        String fkFieldType = relationship.getReferencedPkType();
        fkNotNullCheck.setRightOperand(UnmapJpaRelationshipsUtils.createDefaultValueForPrimitiveType(ast, fkFieldType));

        InfixExpression combinedCondition = ast.newInfixExpression();
        combinedCondition.setLeftOperand(fieldNullCheck);
        combinedCondition.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        combinedCondition.setRightOperand(fkNotNullCheck);

        IfStatement lazyLoadIf = ast.newIfStatement();
        lazyLoadIf.setExpression(combinedCondition);

        VariableDeclarationFragment serviceVarFragment = ast.newVariableDeclarationFragment();
        serviceVarFragment.setName(ast.newSimpleName(serviceName));

        MethodInvocation getServiceCall = ast.newMethodInvocation();
        getServiceCall.setExpression(ast.newName("ServiceFactory"));
        getServiceCall.setName(ast.newSimpleName("get" + serviceClassName));
        serviceVarFragment.setInitializer(getServiceCall);

        VariableDeclarationStatement serviceVarDecl = ast.newVariableDeclarationStatement(serviceVarFragment);
        serviceVarDecl.setType(ast.newSimpleType(ast.newName(serviceClassName)));

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(serviceVarDecl);

        MethodInvocation serviceMethodCall = ast.newMethodInvocation();
        serviceMethodCall.setExpression(ast.newSimpleName(serviceName));
        serviceMethodCall.setName(ast.newSimpleName("get" + simpleTargetEntityName + "ById"));
        serviceMethodCall.arguments().add(ast.newSimpleName(fkFieldName));

        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide(ast.newSimpleName(fieldName));
        assignment.setRightHandSide(serviceMethodCall);

        thenBlock.statements().add(ast.newExpressionStatement(assignment));
        lazyLoadIf.setThenStatement(thenBlock);
        newBody.statements().add(lazyLoadIf);

        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(ast.newSimpleName(fieldName));
        newBody.statements().add(returnStatement);

        String servicePackage = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entity);
        importRewrite.addImport(servicePackage + "." + serviceClassName);
        importRewrite.addImport(servicePackage + ".ServiceFactory");

        return newBody;
    }

    private boolean replaceFieldAccessesWithMethodCalls(
        CompilationUnit astRoot, 
        String fieldName, 
        String methodName, 
        ASTRewrite rewriter, 
        AST ast
    ) {
        FieldToMethodCallVisitor visitor = new FieldToMethodCallVisitor(fieldName, methodName, rewriter, ast);
        astRoot.accept(visitor);
        return visitor.hasChanges();
    }

    // Visitor class to replace field accesses with method calls (except assignments)
    private static class FieldToMethodCallVisitor extends ASTVisitor {
        private String fieldName;
        private String methodName;
        private ASTRewrite rewriter;
        private AST ast;
        private boolean hasChanges = false;

        public FieldToMethodCallVisitor(String fieldName, String methodName, ASTRewrite rewriter, AST ast) {
            this.fieldName = fieldName;
            this.methodName = methodName;
            this.rewriter = rewriter;
            this.ast = ast;
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        @Override
        public boolean visit(SimpleName node) {
            if (fieldName.equals(node.getIdentifier())) {
                if (isFieldReadAccess(node) && !isMethodParameter(node) && !isFieldDeclaration(node)) {
                    MethodInvocation methodCall = ast.newMethodInvocation();
                    methodCall.setName(ast.newSimpleName(methodName));
                    methodCall = addThisExpressionIfNeeded(node, methodCall); // For consistency
                    rewriter.replace(node, methodCall, new TextEditGroup("Replace field access with method call"));
                    hasChanges = true;
                    System.out.println("[DEBUG] Replaced field access: " + fieldName + " -> " + methodName + "()");
                }
            }
            return true;
        }

        private boolean isFieldReadAccess(ASTNode fieldNode) {
            ASTNode parent = fieldNode.getParent();

            // If parent is Assignment and this node is on the LEFT side, it's a write access
            if (parent instanceof Assignment) {
                Assignment assignment = (Assignment) parent;
                if (assignment.getLeftHandSide() == fieldNode) {
                    return false;
                }
            }

            // If parent is FieldAccess (this.fieldName) and this node is on the LEFT side of assignment, it's a write access
            if (parent instanceof FieldAccess) {
                FieldAccess fieldAccess = (FieldAccess) parent;
                ASTNode grandParent = fieldAccess.getParent();
                if (grandParent instanceof Assignment) {
                    Assignment assignment = (Assignment) grandParent;
                    if (assignment.getLeftHandSide() == fieldAccess) {
                        return false;
                    }
                }
            }

            return true;
        }

        // If there is a method parameter with the same name, then we have shadowing for simple accesses
        private boolean isMethodParameter(SimpleName node) {
            ASTNode parent = node.getParent();
            if (parent instanceof FieldAccess) {
                return false;
            }

            while (parent != null) {
                if (parent instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) parent;
                    for (Object param : method.parameters()) {
                        if (param instanceof SingleVariableDeclaration) {
                            SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
                            if (fieldName.equals(svd.getName().getIdentifier())) {
                                return true; // This is a bare parameter reference
                            }
                        }
                    }
                    break;
                }
                parent = parent.getParent();
            }
            return false;
        }

        private boolean isFieldDeclaration(SimpleName node) {
            ASTNode parent = node.getParent();
            if (parent instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
                ASTNode grandParent = fragment.getParent();
                if (grandParent instanceof FieldDeclaration) {
                    return true;
                }
            }
            return false;
        }

        private MethodInvocation addThisExpressionIfNeeded(ASTNode node, MethodInvocation methodCall) {
            ASTNode parent = node.getParent();
            if (parent instanceof FieldAccess) {
                return methodCall;
            }
            methodCall.setExpression(ast.newThisExpression());
            return methodCall;
        }
    }

    private boolean addFKUpdatesForAllAssignments(
        CompilationUnit astRoot, 
        String fieldName, 
        RelationshipInfo relationship, 
        ASTRewrite rewriter, 
        AST ast
    ) {
        FieldAssignmentVisitor visitor = new FieldAssignmentVisitor(fieldName, relationship, rewriter, ast);
        astRoot.accept(visitor);
        return visitor.hasChanges();
    }

    // Visitor class to find ALL assignments to a field and add FK updates
    private static class FieldAssignmentVisitor extends ASTVisitor {
        private String fieldName;
        private RelationshipInfo relationship;
        private ASTRewrite rewriter;
        private AST ast;
        private boolean hasChanges = false;

        public FieldAssignmentVisitor(String fieldName, RelationshipInfo relationship, ASTRewrite rewriter, AST ast) {
            this.fieldName = fieldName;
            this.relationship = relationship;
            this.rewriter = rewriter;
            this.ast = ast;
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        @Override
        public boolean visit(Assignment node) {
            Expression leftSide = node.getLeftHandSide();
            if (isFieldAssignment(leftSide, fieldName)) {
                System.out.println("[DEBUG] Found assignment to field: " + fieldName);
                insertFKUpdateAfterAssignment(node);
                hasChanges = true;
            }
            return true;
        }

        private boolean isFieldAssignment(Expression leftSide, String fieldName) {
            if (leftSide instanceof SimpleName) {
                SimpleName simpleName = (SimpleName) leftSide;
                return fieldName.equals(simpleName.getIdentifier());
            } else if (leftSide instanceof FieldAccess) {
                FieldAccess fieldAccess = (FieldAccess) leftSide;
                return fieldName.equals(fieldAccess.getName().getIdentifier());
            }
            return false;
        }

        private void insertFKUpdateAfterAssignment(Assignment assignment) {
            ASTNode parent = assignment.getParent();
            while (parent != null && !(parent instanceof Statement)) {
                parent = parent.getParent();
            }

            if (parent instanceof ExpressionStatement) {
                ExpressionStatement assignmentStmt = (ExpressionStatement) parent;
                ASTNode blockParent = assignmentStmt.getParent();
                if (blockParent instanceof Block) {
                    Block block = (Block) blockParent;
                    ListRewrite listRewrite = rewriter.getListRewrite(block, Block.STATEMENTS_PROPERTY);
                    Statement fkUpdateStmt = createFKUpdateStatement();
                    listRewrite.insertAfter(fkUpdateStmt, assignmentStmt, new TextEditGroup("Add FK field update"));

                    System.out.println("[DEBUG] Added FK update after assignment to: " + fieldName);
                }
            }
        }

        // Create: this.fkField = (field != null) ? field.getPkMethod() : defaultValue;
        private Statement createFKUpdateStatement() {
            String fkFieldName = relationship.getJoinColumnName();

            Assignment fkAssignment = ast.newAssignment();
            FieldAccess thisFkField = ast.newFieldAccess();
            thisFkField.setExpression(ast.newThisExpression());
            thisFkField.setName(ast.newSimpleName(fkFieldName));
            fkAssignment.setLeftHandSide(thisFkField);

            ConditionalExpression conditional = ast.newConditionalExpression();
            InfixExpression fieldNullCheck = ast.newInfixExpression();
            fieldNullCheck.setLeftOperand(ast.newSimpleName(fieldName));
            fieldNullCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
            fieldNullCheck.setRightOperand(ast.newNullLiteral());
            conditional.setExpression(fieldNullCheck);

            MethodInvocation getPkCall = ast.newMethodInvocation();
            getPkCall.setExpression(ast.newSimpleName(fieldName));
            String pkGetterName = "get" + UnmapJpaRelationshipsUtils.capitalize(relationship.getReferencedPkName());
            getPkCall.setName(ast.newSimpleName(pkGetterName));
            conditional.setThenExpression(getPkCall);
            conditional.setElseExpression(UnmapJpaRelationshipsUtils.createDefaultValueForPrimitiveType(ast, relationship.getReferencedPkType()));
            fkAssignment.setRightHandSide(conditional);

            return ast.newExpressionStatement(fkAssignment);
        }
    }
}