package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.SystemObject;
import gr.aueb.java.archifactor.jpa.enums.JpaJoinType;
import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.enums.PersistenceNamespace;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.JpaAnnotationExtractorUtils;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.Access;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;

public class EntityTransformer {
    private SystemObject systemObject;
    private PersistenceNamespace persistenceNamespace;
    private Map<ICompilationUnit, CompilationUnit> astRootMap = new HashMap<>();
    private Map<ICompilationUnit, ASTRewrite> rewriterMap = new HashMap<>();
    private Map<ICompilationUnit, ImportRewrite> importRewriteMap = new HashMap<>();
    private Map<CompilationUnit, MethodDeclaration> fkSyncMethodMap = new HashMap<>();

    public EntityTransformer(SystemObject systemObject, Map<String, List<ServiceMethodProvider>> serviceMethodRequirements, PersistenceNamespace persistenceNamespace) {
        this.systemObject = systemObject;
        this.persistenceNamespace = persistenceNamespace;
    }

    public void transformFromEntity(ClassObject entity, RelationshipInfo relationship, Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges) throws Exception {
        ICompilationUnit cu = (ICompilationUnit) entity.getITypeRoot();
        if (cu == null) {
            return;
        }

        CompilationUnit astRoot;
        ASTRewrite rewriter;
        ImportRewrite importRewrite;
        if (astRootMap.containsKey(cu)) {
            astRoot = astRootMap.get(cu);
            rewriter = rewriterMap.get(cu);
            importRewrite = importRewriteMap.get(cu);
        } else {
            ASTParser parser = ASTParser.newParser(ASTReader.JLS);
            parser.setSource(cu);
            parser.setResolveBindings(true);
            astRoot = (CompilationUnit) parser.createAST(null);

            rewriter = ASTRewrite.create(astRoot.getAST());
            importRewrite = ImportRewrite.create(astRoot, true);

            astRootMap.put(cu, astRoot);
            rewriterMap.put(cu, rewriter);
            importRewriteMap.put(cu, importRewrite);
        }

        // 1. Transform field declaration
        FieldDeclaration fieldDecl = findFieldDeclaration(astRoot, relationship.getFieldName());
        if (fieldDecl == null) {
            throw new IllegalStateException("Field " + relationship.getFieldName() + " not found in " + entity.getName() + ". No changes were applied.");
        }
        System.out.println("[DEBUG] Found field declaration for: " + relationship.getFieldName() + " in " + entity.getName());
        boolean hasChanges = transformFieldDeclaration(fieldDecl, relationship, rewriter, importRewrite, astRoot.getAST(), entity);

        // 2. Transform methods in the SAME AST operation
        hasChanges |= transformMethodsInSameAST(astRoot, entity, relationship, relationship.getFieldName(), rewriter, importRewrite);

        // 3. Add SyncingSet inner class for ManyToMany owning side
        if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY && relationship.isOwningSide()) {
            System.out.println("[DEBUG] Adding SyncingSet inner class for ManyToMany owning side");
            hasChanges |= addSyncingSetInnerClass(astRoot, entity, relationship, rewriter, importRewrite, astRoot.getAST());
        }

        // 4. Re-sync the FK column at flush time for the owning to-one side,
        // because the setter captures the id before the referenced entity may have one
        if (relationship.isOwningSide() && relationship.getRelationshipType() != JpaRelationshipType.MANY_TO_MANY) {
            hasChanges |= addForeignKeyFlushSync(astRoot, relationship, rewriter, importRewrite, astRoot.getAST());
        }

        if (!hasChanges) {
            return;
        }

        CompilationUnitChange change = new CompilationUnitChange(cu.getElementName(), cu);
        change.setEdit(rewriter.rewriteAST());
        if (importRewrite.hasRecordedChanges()) {
            change.addEdit(importRewrite.rewriteImports(null));
        }
        compilationUnitChanges.put(cu, change);
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
                if (JpaRelationshipType.isRelationshipType(annotationName) || JpaJoinType.isJoinType(annotationName)) {
                    modifiersRewrite.remove(annotation, editGroup);
                    removedAnnotations = true;
                }
            }
        }

        // Add the @Transient annotation to the transformed field
        boolean hasChanges = false;
        if (removedAnnotations) {
            importRewrite.addImport(persistenceNamespace.type("persistence.Transient"));

            MarkerAnnotation transientAnnotation = ast.newMarkerAnnotation();
            transientAnnotation.setTypeName(ast.newName("Transient"));
            modifiersRewrite.insertFirst(transientAnnotation, editGroup);
            hasChanges = true;
        }

        if (relationship.isOwningSide()) {
            if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY) {
                System.out.println("[DEBUG] Transforming ManyToMany owning side: " + entity.getName() + "." + relationship.getFieldName());
                hasChanges |= changeFieldInitializationToSyncingSet(fieldDecl, relationship, rewriter, ast, editGroup, entity);
                hasChanges |= addElementCollectionField(fieldDecl, relationship, rewriter, importRewrite, ast, editGroup);
            } else {
                System.out.println("[DEBUG] Adding FK field for single reference: " + entity.getName() + "." + relationship.getFieldName());
                hasChanges |= addForeignKeyField(fieldDecl, relationship, rewriter, importRewrite, ast, editGroup);
            }
        } else {
            hasChanges |= ensureCollectionInitialization(fieldDecl, relationship, rewriter, importRewrite, ast, editGroup);
        }
        return hasChanges;
    }

    private boolean changeFieldInitializationToSyncingSet(
        FieldDeclaration fieldDecl,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        AST ast,
        TextEditGroup editGroup,
        ClassObject entity
    ) {
        String fieldName = relationship.getFieldName();
        String targetEntityName = relationship.getToEntity();
        String simpleTargetEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(targetEntityName);
        String syncingSetClassName = "Syncing" + simpleTargetEntityName + "Set";

        for (Object fragment : fieldDecl.fragments()) {
            if (fragment instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                if (fieldName.equals(vdf.getName().getIdentifier())) {
                    ClassInstanceCreation newInit = ast.newClassInstanceCreation();
                    newInit.setType(ast.newSimpleType(ast.newName(syncingSetClassName)));
                    rewriter.replace(vdf.getInitializer(), newInit, editGroup);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean addElementCollectionField(
        FieldDeclaration originalField,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast,
        TextEditGroup editGroup
    ) {
        String elementCollectionFieldName = relationship.getJoinTableInverseJoinColumns() + "s";
        String elementType = relationship.getReferencedPkType();
        String simpleElementType = UnmapJpaRelationshipsUtils.getSimpleClassName(elementType);
        
        importRewrite.addImport(persistenceNamespace.type("persistence.Column"));
        importRewrite.addImport(persistenceNamespace.type("persistence.CollectionTable"));
        importRewrite.addImport(persistenceNamespace.type("persistence.ElementCollection"));
        importRewrite.addImport(persistenceNamespace.type("persistence.JoinColumn"));
        if (elementType.contains(".") && !elementType.contains("lang")) {
            importRewrite.addImport(elementType);
        }

        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(elementCollectionFieldName));

        ClassInstanceCreation init = ast.newClassInstanceCreation();
        ParameterizedType hashSetType = ast.newParameterizedType(ast.newSimpleType(ast.newName("HashSet")));
        init.setType(hashSetType);
        fragment.setInitializer(init);

        FieldDeclaration elementCollectionField = ast.newFieldDeclaration(fragment);
        ParameterizedType setType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Set")));
        setType.typeArguments().add(ast.newSimpleType(ast.newName(simpleElementType)));
        elementCollectionField.setType(setType);

        MarkerAnnotation elementCollectionAnnotation = ast.newMarkerAnnotation();
        elementCollectionAnnotation.setTypeName(ast.newName("ElementCollection"));
        elementCollectionField.modifiers().add(elementCollectionAnnotation);

        NormalAnnotation collectionTableAnnotation = ast.newNormalAnnotation();
        collectionTableAnnotation.setTypeName(ast.newName("CollectionTable"));

        MemberValuePair nameAttr = ast.newMemberValuePair();
        nameAttr.setName(ast.newSimpleName("name"));
        StringLiteral tableNameLiteral = ast.newStringLiteral();
        tableNameLiteral.setLiteralValue(relationship.getJoinTableName());
        nameAttr.setValue(tableNameLiteral);
        collectionTableAnnotation.values().add(nameAttr);

        MemberValuePair joinColumnsAttr = ast.newMemberValuePair();
        joinColumnsAttr.setName(ast.newSimpleName("joinColumns"));
        NormalAnnotation joinColumnAnnotation = ast.newNormalAnnotation();
        joinColumnAnnotation.setTypeName(ast.newName("JoinColumn"));
        MemberValuePair joinColumnName = ast.newMemberValuePair();
        joinColumnName.setName(ast.newSimpleName("name"));
        StringLiteral joinColumnNameLiteral = ast.newStringLiteral();
        joinColumnNameLiteral.setLiteralValue(relationship.getJoinTableJoinColumns());
        joinColumnName.setValue(joinColumnNameLiteral);
        joinColumnAnnotation.values().add(joinColumnName);
        joinColumnsAttr.setValue(joinColumnAnnotation);
        collectionTableAnnotation.values().add(joinColumnsAttr);
        elementCollectionField.modifiers().add(collectionTableAnnotation);

        NormalAnnotation columnAnnotation = ast.newNormalAnnotation();
        columnAnnotation.setTypeName(ast.newName("Column"));
        MemberValuePair columnNameAttr = ast.newMemberValuePair();
        columnNameAttr.setName(ast.newSimpleName("name"));
        StringLiteral columnNameLiteral = ast.newStringLiteral();
        columnNameLiteral.setLiteralValue(relationship.getJoinTableInverseJoinColumns());
        columnNameAttr.setValue(columnNameLiteral);
        columnAnnotation.values().add(columnNameAttr);
        elementCollectionField.modifiers().add(columnAnnotation);
        elementCollectionField.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

        TypeDeclaration parentType = (TypeDeclaration) originalField.getParent();
        ListRewrite fieldsRewrite = rewriter.getListRewrite(parentType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        fieldsRewrite.insertAfter(elementCollectionField, originalField, editGroup);
        return true;
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
        importRewrite.addImport(persistenceNamespace.type("persistence.Column"));
        if (relationship.getReferencedPkType().contains(".") && !relationship.getReferencedPkType().contains("lang")) {
            importRewrite.addImport(relationship.getReferencedPkType());
        }

        // Insert the field after the original field
        TypeDeclaration parentType = (TypeDeclaration) originalField.getParent();
        ListRewrite fieldsRewrite = rewriter.getListRewrite(parentType, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        fieldsRewrite.insertAfter(fkField, originalField, editGroup);
        return true;
    }

    private boolean ensureCollectionInitialization(
        FieldDeclaration fieldDecl,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast,
        TextEditGroup editGroup
    ) {
        Type fieldType = fieldDecl.getType();
        if (!(fieldType instanceof ParameterizedType)) {
            return false;
        }

        ParameterizedType paramType = (ParameterizedType) fieldType;
        Type rawType = paramType.getType();
        if (!(rawType instanceof SimpleType)) {
            return false;
        }

        String typeName = ((SimpleType) rawType).getName().getFullyQualifiedName();
        if (!typeName.equals("Set") && !typeName.equals("List")) {
            return false;
        }

        for (Object fragment : fieldDecl.fragments()) {
            if (fragment instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                if (vdf.getName().getIdentifier().equals(relationship.getFieldName())) {
                    if (vdf.getInitializer() == null) {
                        ClassInstanceCreation init = ast.newClassInstanceCreation();
                        ParameterizedType initType;
                        if (typeName.equals("Set")) {
                            initType = ast.newParameterizedType(ast.newSimpleType(ast.newName("HashSet")));
                            importRewrite.addImport("java.util.HashSet");
                        } else {
                            initType = ast.newParameterizedType(ast.newSimpleType(ast.newName("ArrayList")));
                            importRewrite.addImport("java.util.ArrayList");
                        }
                        init.setType(initType);
                        rewriter.set(vdf, VariableDeclarationFragment.INITIALIZER_PROPERTY, init, editGroup);
                        return true;
                    }
                }
            }
        }
        return false;
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
            String conventionalGetterName = "get" + UnmapJpaRelationshipsUtils.capitalize(targetFieldName);
            MethodDeclaration conventionalGetter = findMethodByName(astRoot, conventionalGetterName);
            if (conventionalGetter != null) {
                System.out.println("[DEBUG] Found conventional getter " + conventionalGetterName + "(), creating private " + conventionalGetterName + "Inner()");
                String innerMethodName = conventionalGetterName + "Inner";
                directAccessMethod = createPrivateDirectFieldAccessMethod(astRoot, targetFieldName, innerMethodName, entity, relationship, rewriter, ast);
                directAccessMethodName = innerMethodName;
                hasChanges = true;
            } else {
                System.out.println("[DEBUG] No conventional getter found, creating public " + conventionalGetterName + "()");
                directAccessMethod = createDirectFieldAccessMethod(astRoot, targetFieldName, entity, relationship, rewriter, ast);
                directAccessMethodName = directAccessMethod.getName().getIdentifier();
                hasChanges = true;
            }
        } else {
            directAccessMethodName = directAccessMethod.getName().getIdentifier();
            System.out.println("[DEBUG] Found direct field access method: " + directAccessMethodName);
        }

        // 2. Transform the direct access method with lazy loading
        hasChanges |= transformDirectAccessMethodInAST(directAccessMethod, entity, relationship, targetFieldName, rewriter, importRewrite, ast);

        // 3. Replace all field accesses with method calls (except assignments)
        hasChanges |= replaceFieldAccessesWithMethodCalls(astRoot, targetFieldName, directAccessMethodName, rewriter, ast);

        // 4. Handle field assignments for owning side
        if (relationship.isOwningSide()) {
            if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY) {
                System.out.println("[DEBUG] Replacing assignments with setter calls for ManyToMany owning side field: " + targetFieldName);
                hasChanges |= replaceAssignmentsWithSetterCalls(astRoot, targetFieldName, directAccessMethodName, entity, relationship, rewriter, importRewrite, ast);
            } else {
                System.out.println("[DEBUG] Adding FK updates for all assignments to owning side field: " + targetFieldName);
                hasChanges |= addFKUpdatesForAllAssignments(astRoot, targetFieldName, relationship, rewriter, ast);
            }
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

    private MethodDeclaration findMethodByName(CompilationUnit astRoot, String methodName) {
        for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
            for (MethodDeclaration method : type.getMethods()) {
                if (methodName.equals(method.getName().getIdentifier())) {
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

    private MethodDeclaration createPrivateDirectFieldAccessMethod(
        CompilationUnit astRoot,
        String fieldName,
        String methodName,
        ClassObject entity,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        AST ast
    ) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName(methodName));
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

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

        TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        methodsRewrite.insertLast(method, new TextEditGroup("Add private inner field access method"));
        System.out.println("[DEBUG] Created private inner getter: " + methodName + "() returning " + returnType);
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
        
        String servicePackage = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entity);
        importRewrite.addImport(servicePackage + "." + serviceClassName);
        importRewrite.addImport(servicePackage + ".ServiceFactory");

		ServiceMethodProvider provider = ServiceMethodProviderFactory.createProvider(relationship);
        if (provider == null) {
            throw new IllegalStateException("No service method provider found for the " + relationship.getRelationshipType()
            	+ " relationship: " + relationship.getFromEntity() + " -> " + relationship.getToEntity());
        }
        String serviceMethodName = provider.getMethodName();

        Expression condition;
        Expression serviceMethodArgument;
        if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY && relationship.isOwningSide()) {
            String elementCollectionFieldName = relationship.getJoinTableInverseJoinColumns() + "s";
            serviceMethodArgument = ast.newSimpleName(elementCollectionFieldName);
            condition = buildManyToManyOwningCondition(ast, fieldName, elementCollectionFieldName);
        } else {
            JpaAnnotationExtractorUtils jpaAnnotationExtractor = new JpaAnnotationExtractorUtils(systemObject);
            serviceMethodArgument = createIdAccessExpression(ast, entity, jpaAnnotationExtractor.extractIdFieldName(entity.getName()));
            condition = buildNonOwningCondition(ast, fieldName, entity, jpaAnnotationExtractor);
        }

        IfStatement lazyLoadIf = ast.newIfStatement();
        lazyLoadIf.setExpression(condition);

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(createServiceVariableDeclaration(ast, serviceName, serviceClassName));
        thenBlock.statements().add(createAddAllStatement(ast, fieldName, serviceName, serviceMethodName, serviceMethodArgument));
        lazyLoadIf.setThenStatement(thenBlock);
        newBody.statements().add(lazyLoadIf);

        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(ast.newSimpleName(fieldName));
        newBody.statements().add(returnStatement);
        return newBody;
    }

    private Expression buildManyToManyOwningCondition(AST ast, String fieldName, String elementCollectionFieldName) {
        MethodInvocation fieldIsEmptyCall = ast.newMethodInvocation();
        fieldIsEmptyCall.setExpression(ast.newSimpleName(fieldName));
        fieldIsEmptyCall.setName(ast.newSimpleName("isEmpty"));

        MethodInvocation elementCollectionIsEmptyCall = ast.newMethodInvocation();
        elementCollectionIsEmptyCall.setExpression(ast.newSimpleName(elementCollectionFieldName));
        elementCollectionIsEmptyCall.setName(ast.newSimpleName("isEmpty"));

        PrefixExpression notEmpty = ast.newPrefixExpression();
        notEmpty.setOperator(PrefixExpression.Operator.NOT);
        notEmpty.setOperand(elementCollectionIsEmptyCall);

        InfixExpression combined = ast.newInfixExpression();
        combined.setLeftOperand(fieldIsEmptyCall);
        combined.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        combined.setRightOperand(notEmpty);

        MethodInvocation isContainerAvailableCall = ast.newMethodInvocation();
        isContainerAvailableCall.setExpression(ast.newName("ServiceFactory"));
        isContainerAvailableCall.setName(ast.newSimpleName("isContainerAvailable"));

        InfixExpression finalCondition = ast.newInfixExpression();
        finalCondition.setLeftOperand(combined);
        finalCondition.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        finalCondition.setRightOperand(isContainerAvailableCall);
        return finalCondition;
    }

    private Expression buildNonOwningCondition(AST ast, String fieldName, ClassObject entity, JpaAnnotationExtractorUtils jpaAnnotationExtractor) {
        MethodInvocation fieldIsEmptyCall = ast.newMethodInvocation();
        fieldIsEmptyCall.setExpression(ast.newSimpleName(fieldName));
        fieldIsEmptyCall.setName(ast.newSimpleName("isEmpty"));

        String idFieldName = jpaAnnotationExtractor.extractIdFieldName(entity.getName());
        String idFieldType = jpaAnnotationExtractor.extractIdFieldType(entity.getName());

        InfixExpression idNotNullCheck = ast.newInfixExpression();
        idNotNullCheck.setLeftOperand(createIdAccessExpression(ast, entity, idFieldName));
        idNotNullCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
        idNotNullCheck.setRightOperand(UnmapJpaRelationshipsUtils.createDefaultValueForPrimitiveType(ast, idFieldType));

        InfixExpression combined = ast.newInfixExpression();
        combined.setLeftOperand(fieldIsEmptyCall);
        combined.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        combined.setRightOperand(idNotNullCheck);

        MethodInvocation isContainerAvailableCall = ast.newMethodInvocation();
        isContainerAvailableCall.setExpression(ast.newName("ServiceFactory"));
        isContainerAvailableCall.setName(ast.newSimpleName("isContainerAvailable"));

        InfixExpression finalCondition = ast.newInfixExpression();
        finalCondition.setLeftOperand(combined);
        finalCondition.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        finalCondition.setRightOperand(isContainerAvailableCall);
        return finalCondition;
    }

    // Reads the id of the entity being transformed. An id inherited from a @MappedSuperclass can
    // be private there, so we have to read it through its getter instead of the field.
    private Expression createIdAccessExpression(AST ast, ClassObject entity, String idFieldName) {
        if (getFieldFromEntity(idFieldName, entity) != null) {
            return ast.newSimpleName(idFieldName);
        }

        Optional<String> idGetterName = findIdGetterName(entity.getName(), idFieldName);
        if (idGetterName.isPresent()) {
            MethodInvocation idGetterCall = ast.newMethodInvocation();
            idGetterCall.setName(ast.newSimpleName(idGetterName.get()));
            return idGetterCall;
        }

        if (isInheritedFieldAccessible(entity, idFieldName)) {
            return ast.newSimpleName(idFieldName);
        }

        throw new IllegalStateException("The id of " + entity.getName() + " is inherited, but it is private and has no get" + UnmapJpaRelationshipsUtils.capitalize(idFieldName) + "() method.");
    }

    private boolean isInheritedFieldAccessible(ClassObject entity, String fieldName) {
        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(systemObject);
        for (ClassObject superclass : jpaExtractor.getMappedHierarchy(entity.getName())) {
            FieldObject field = getFieldFromEntity(fieldName, superclass);
            if (field == null) {
                continue;
            }

            if (field.getAccess() == Access.PUBLIC || field.getAccess() == Access.PROTECTED) {
                return true;
            }

            return field.getAccess() == Access.NONE
                && UnmapJpaRelationshipsUtils.getPackageNameFromClass(entity).equals(UnmapJpaRelationshipsUtils.getPackageNameFromClass(superclass));
        }
        return false;
    }

    private VariableDeclarationStatement createServiceVariableDeclaration(AST ast, String serviceName, String serviceClassName) {
        VariableDeclarationFragment serviceVarFragment = ast.newVariableDeclarationFragment();
        serviceVarFragment.setName(ast.newSimpleName(serviceName));

        MethodInvocation getServiceCall = ast.newMethodInvocation();
        getServiceCall.setExpression(ast.newName("ServiceFactory"));
        getServiceCall.setName(ast.newSimpleName("get" + serviceClassName));
        serviceVarFragment.setInitializer(getServiceCall);

        VariableDeclarationStatement serviceVarDecl = ast.newVariableDeclarationStatement(serviceVarFragment);
        serviceVarDecl.setType(ast.newSimpleType(ast.newName(serviceClassName)));
        return serviceVarDecl;
    }

    private ExpressionStatement createAddAllStatement(AST ast, String fieldName, String serviceName, String serviceMethodName, Expression serviceMethodArgument) {
        MethodInvocation addAllCall = ast.newMethodInvocation();
        addAllCall.setExpression(ast.newSimpleName(fieldName));
        addAllCall.setName(ast.newSimpleName("addAll"));

        MethodInvocation serviceMethodCall = ast.newMethodInvocation();
        serviceMethodCall.setExpression(ast.newSimpleName(serviceName));
        serviceMethodCall.setName(ast.newSimpleName(serviceMethodName));
        serviceMethodCall.arguments().add(serviceMethodArgument);
        addAllCall.arguments().add(serviceMethodCall);
        return ast.newExpressionStatement(addAllCall);
    }

    private Block createSimpleTypeLazyLoadingBlock(AST ast, String fieldName, ClassObject entity, RelationshipInfo relationship, ImportRewrite importRewrite) {
        Block newBody = ast.newBlock();

        String targetEntityName = relationship.getToEntity();
        String simpleTargetEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(targetEntityName);
        String serviceName = UnmapJpaRelationshipsUtils.decapitalize(simpleTargetEntityName) + "Service";
        String serviceClassName = simpleTargetEntityName + "Service";

        ServiceMethodProvider provider = ServiceMethodProviderFactory.createProvider(relationship);
        if (provider == null) {
            throw new IllegalStateException("No service method provider found for the " + relationship.getRelationshipType()
            	+ " relationship: " + relationship.getFromEntity() + " -> " + relationship.getToEntity());
        }
        String serviceMethodName = provider.getMethodName();

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

        MethodInvocation isContainerAvailableCall = ast.newMethodInvocation();
        isContainerAvailableCall.setExpression(ast.newName("ServiceFactory"));
        isContainerAvailableCall.setName(ast.newSimpleName("isContainerAvailable"));

        InfixExpression finalCondition = ast.newInfixExpression();
        finalCondition.setLeftOperand(combinedCondition);
        finalCondition.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        finalCondition.setRightOperand(isContainerAvailableCall);

        IfStatement lazyLoadIf = ast.newIfStatement();
        lazyLoadIf.setExpression(finalCondition);

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
        serviceMethodCall.setName(ast.newSimpleName(serviceMethodName));
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

    private boolean addForeignKeyFlushSync(
        CompilationUnit astRoot,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast
    ) {
        MethodDeclaration prePersistHost = findCallbackMethod(astRoot, "PrePersist");
        MethodDeclaration preUpdateHost = findCallbackMethod(astRoot, "PreUpdate");

        // JPA allows at most one callback method per lifecycle event per class,
        // so reuse existing callbacks instead of adding a second one
        if (prePersistHost != null) {
            prependStatementToCallback(prePersistHost, createFKSyncStatement(ast, relationship), rewriter);
            System.out.println("[DEBUG] Added FK sync to existing @PrePersist callback: " + prePersistHost.getName().getIdentifier());
        }
        if (preUpdateHost != null && preUpdateHost != prePersistHost) {
            prependStatementToCallback(preUpdateHost, createFKSyncStatement(ast, relationship), rewriter);
            System.out.println("[DEBUG] Added FK sync to existing @PreUpdate callback: " + preUpdateHost.getName().getIdentifier());
        }
        if (prePersistHost != null && preUpdateHost != null) {
            return true;
        }

        MethodDeclaration syncMethod = fkSyncMethodMap.get(astRoot);
        if (syncMethod == null) {
            syncMethod = createFKSyncCallbackMethod(astRoot, ast, rewriter, importRewrite, prePersistHost == null, preUpdateHost == null);
            fkSyncMethodMap.put(astRoot, syncMethod);
        }
        syncMethod.getBody().statements().add(createFKSyncStatement(ast, relationship));
        return true;
    }

    private MethodDeclaration findCallbackMethod(CompilationUnit astRoot, String annotationName) {
        for (TypeDeclaration type : (List<TypeDeclaration>) astRoot.types()) {
            for (MethodDeclaration method : type.getMethods()) {
                if (method.getBody() == null) {
                    continue;
                }
                for (Object modifier : method.modifiers()) {
                    if (modifier instanceof Annotation) {
                        String name = ((Annotation) modifier).getTypeName().getFullyQualifiedName();
                        if (annotationName.equals(name) || name.endsWith("." + annotationName)) {
                            return method;
                        }
                    }
                }
            }
        }
        return null;
    }

    // Insert at the top of the callback so an early return in the existing body cannot skip the sync
    private void prependStatementToCallback(MethodDeclaration callback, Statement statement, ASTRewrite rewriter) {
        ListRewrite statementsRewrite = rewriter.getListRewrite(callback.getBody(), Block.STATEMENTS_PROPERTY);
        statementsRewrite.insertFirst(statement, new TextEditGroup("Add FK sync to lifecycle callback"));
    }

    private MethodDeclaration createFKSyncCallbackMethod(
        CompilationUnit astRoot,
        AST ast,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        boolean onPersist,
        boolean onUpdate
    ) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("syncUnmappedForeignKeys"));
        if (onPersist) {
            importRewrite.addImport(persistenceNamespace.type("persistence.PrePersist"));
            MarkerAnnotation prePersist = ast.newMarkerAnnotation();
            prePersist.setTypeName(ast.newName("PrePersist"));
            method.modifiers().add(prePersist);
        }
        if (onUpdate) {
            importRewrite.addImport(persistenceNamespace.type("persistence.PreUpdate"));
            MarkerAnnotation preUpdate = ast.newMarkerAnnotation();
            preUpdate.setTypeName(ast.newName("PreUpdate"));
            method.modifiers().add(preUpdate);
        }
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
        method.setBody(ast.newBlock());

        TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        methodsRewrite.insertLast(method, new TextEditGroup("Add FK sync lifecycle callback"));
        System.out.println("[DEBUG] Created FK sync lifecycle callback: syncUnmappedForeignKeys()");
        return method;
    }

    // Create: if (this.field != null) { this.fkField = this.field.getPkMethod(); }
    // Reads the field directly (not the getter) so the callback never triggers a lazy load,
    // and never nulls the FK: a null reference can also mean "not loaded".
    // Reads are this-qualified so local variables in a pre-existing callback cannot shadow the field.
    private Statement createFKSyncStatement(AST ast, RelationshipInfo relationship) {
        InfixExpression fieldNotNull = ast.newInfixExpression();
        FieldAccess thisFieldGuard = ast.newFieldAccess();
        thisFieldGuard.setExpression(ast.newThisExpression());
        thisFieldGuard.setName(ast.newSimpleName(relationship.getFieldName()));
        fieldNotNull.setLeftOperand(thisFieldGuard);
        fieldNotNull.setOperator(InfixExpression.Operator.NOT_EQUALS);
        fieldNotNull.setRightOperand(ast.newNullLiteral());

        Assignment fkAssignment = ast.newAssignment();
        FieldAccess thisFkField = ast.newFieldAccess();
        thisFkField.setExpression(ast.newThisExpression());
        thisFkField.setName(ast.newSimpleName(relationship.getJoinColumnName()));
        fkAssignment.setLeftHandSide(thisFkField);

        MethodInvocation getPkCall = ast.newMethodInvocation();
        FieldAccess thisFieldRead = ast.newFieldAccess();
        thisFieldRead.setExpression(ast.newThisExpression());
        thisFieldRead.setName(ast.newSimpleName(relationship.getFieldName()));
        getPkCall.setExpression(thisFieldRead);
        String pkGetterName = "get" + UnmapJpaRelationshipsUtils.capitalize(relationship.getReferencedPkName());
        getPkCall.setName(ast.newSimpleName(pkGetterName));
        fkAssignment.setRightHandSide(getPkCall);

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(ast.newExpressionStatement(fkAssignment));

        IfStatement ifStatement = ast.newIfStatement();
        ifStatement.setExpression(fieldNotNull);
        ifStatement.setThenStatement(thenBlock);
        return ifStatement;
    }

    private boolean replaceAssignmentsWithSetterCalls(
        CompilationUnit astRoot,
        String fieldName,
        String getterMethodName,
        ClassObject entity,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast
    ) {
        AssignmentDetectorVisitor detector = new AssignmentDetectorVisitor(fieldName);
        astRoot.accept(detector);

        if (!detector.hasAssignments()) {
            System.out.println("[DEBUG] No assignments found for field: " + fieldName);
            return false;
        }

        MethodDeclaration setterMethod = createSetterMethodForManyToMany(astRoot, fieldName, getterMethodName, entity, relationship, rewriter, importRewrite, ast);
        String setterName = setterMethod.getName().getIdentifier();

        AssignmentReplacementVisitor replacer = new AssignmentReplacementVisitor(fieldName, setterName, rewriter, ast);
        astRoot.accept(replacer);
        return replacer.hasChanges();
    }

    // Visitor class to detect if there are any assignments to a field
    private static class AssignmentDetectorVisitor extends ASTVisitor {
        private String fieldName;
        private boolean foundAssignment = false;

        public AssignmentDetectorVisitor(String fieldName) {
            this.fieldName = fieldName;
        }

        public boolean hasAssignments() {
            return foundAssignment;
        }

        @Override
        public boolean visit(Assignment node) {
            Expression leftSide = node.getLeftHandSide();
            if (isFieldAssignment(leftSide)) {
                foundAssignment = true;
                return false;
            }
            return true;
        }

        private boolean isFieldAssignment(Expression leftSide) {
            if (leftSide instanceof SimpleName) {
                return fieldName.equals(((SimpleName) leftSide).getIdentifier());
            } else if (leftSide instanceof FieldAccess) {
                return fieldName.equals(((FieldAccess) leftSide).getName().getIdentifier());
            }
            return false;
        }
    }

    private MethodDeclaration createSetterMethodForManyToMany(
        CompilationUnit astRoot,
        String fieldName,
        String getterMethodName,
        ClassObject entity,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast
    ) {
        importRewrite.addImport("java.util.Objects");

        String conventionalSetterName = "set" + UnmapJpaRelationshipsUtils.capitalize(fieldName);
        MethodDeclaration existingSetter = findMethodByName(astRoot, conventionalSetterName);

        String setterName;
        boolean isPrivate = false;
        if (existingSetter != null) {
            setterName = conventionalSetterName + "Inner";
            isPrivate = true;
            System.out.println("[DEBUG] Found existing setter " + conventionalSetterName + "(), creating private " + setterName + "()");
        } else {
            setterName = conventionalSetterName;
            System.out.println("[DEBUG] No setter found, creating " + setterName + "()");
        }

        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName(setterName));

        if (isPrivate) {
            method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
        }

        FieldObject fieldObj = getFieldFromEntity(fieldName, entity);
        String genericType = fieldObj.getType().getGenericType();
        ParameterizedType paramType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Set")));
        String elementType = UnmapJpaRelationshipsUtils.getSimpleClassName(genericType.replaceAll("[<>]", "").trim());
        paramType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setType(paramType);
        String paramName = "new" + UnmapJpaRelationshipsUtils.capitalize(fieldName);
        param.setName(ast.newSimpleName(paramName));
        method.parameters().add(param);

        Block body = ast.newBlock();

        VariableDeclarationFragment liveFragment = ast.newVariableDeclarationFragment();
        liveFragment.setName(ast.newSimpleName("live"));
        MethodInvocation getterCall = ast.newMethodInvocation();
        getterCall.setName(ast.newSimpleName(getterMethodName));
        liveFragment.setInitializer(getterCall);
        VariableDeclarationStatement liveDecl = ast.newVariableDeclarationStatement(liveFragment);
        liveDecl.setType((ParameterizedType) ASTNode.copySubtree(ast, paramType));
        body.statements().add(liveDecl);

        MethodInvocation equalsCall = ast.newMethodInvocation();
        equalsCall.setExpression(ast.newName("Objects"));
        equalsCall.setName(ast.newSimpleName("equals"));
        equalsCall.arguments().add(ast.newSimpleName("live"));
        equalsCall.arguments().add(ast.newSimpleName(paramName));

        Block equalsThenBlock = ast.newBlock();
        equalsThenBlock.statements().add(ast.newReturnStatement());

        IfStatement equalsCheck = ast.newIfStatement();
        equalsCheck.setExpression(equalsCall);
        equalsCheck.setThenStatement(equalsThenBlock);
        body.statements().add(equalsCheck);

        MethodInvocation clearCall = ast.newMethodInvocation();
        clearCall.setExpression(ast.newSimpleName("live"));
        clearCall.setName(ast.newSimpleName("clear"));
        body.statements().add(ast.newExpressionStatement(clearCall));

        InfixExpression notNullCheck = ast.newInfixExpression();
        notNullCheck.setLeftOperand(ast.newSimpleName(paramName));
        notNullCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
        notNullCheck.setRightOperand(ast.newNullLiteral());

        MethodInvocation addAllCall = ast.newMethodInvocation();
        addAllCall.setExpression(ast.newSimpleName("live"));
        addAllCall.setName(ast.newSimpleName("addAll"));
        addAllCall.arguments().add(ast.newSimpleName(paramName));

        Block nullCheckThenBlock = ast.newBlock();
        nullCheckThenBlock.statements().add(ast.newExpressionStatement(addAllCall));

        IfStatement nullCheck = ast.newIfStatement();
        nullCheck.setExpression(notNullCheck);
        nullCheck.setThenStatement(nullCheckThenBlock);
        body.statements().add(nullCheck);

        method.setBody(body);

        TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        methodsRewrite.insertLast(method, new TextEditGroup("Add setter method for ManyToMany"));
        return method;
    }

    // Visitor class to replace assignments with setter calls
    private static class AssignmentReplacementVisitor extends ASTVisitor {
        private String fieldName;
        private String setterName;
        private ASTRewrite rewriter;
        private AST ast;
        private boolean hasChanges = false;

        public AssignmentReplacementVisitor(String fieldName, String setterName, ASTRewrite rewriter, AST ast) {
            this.fieldName = fieldName;
            this.setterName = setterName;
            this.rewriter = rewriter;
            this.ast = ast;
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        @Override
        public boolean visit(Assignment node) {
            Expression leftSide = node.getLeftHandSide();
            if (isFieldAssignment(leftSide)) {
                replaceAssignmentWithSetterCall(node);
                hasChanges = true;
            }
            return true;
        }

        private boolean isFieldAssignment(Expression leftSide) {
            if (leftSide instanceof SimpleName) {
                return fieldName.equals(((SimpleName) leftSide).getIdentifier());
            } else if (leftSide instanceof FieldAccess) {
                return fieldName.equals(((FieldAccess) leftSide).getName().getIdentifier());
            }
            return false;
        }

        private void replaceAssignmentWithSetterCall(Assignment assignment) {
            ASTNode parent = assignment.getParent();
            while (parent != null && !(parent instanceof Statement)) {
                parent = parent.getParent();
            }

            if (parent instanceof ExpressionStatement) {
                ExpressionStatement assignmentStmt = (ExpressionStatement) parent;

                MethodInvocation setterCall = ast.newMethodInvocation();
                setterCall.setExpression(ast.newThisExpression());
                setterCall.setName(ast.newSimpleName(setterName));
                setterCall.arguments().add((Expression) ASTNode.copySubtree(ast, assignment.getRightHandSide()));

                ExpressionStatement newStmt = ast.newExpressionStatement(setterCall);
                rewriter.replace(assignmentStmt, newStmt, new TextEditGroup("Replace assignment with setter call"));
            }
        }
    }

    private boolean addSyncingSetInnerClass(
        CompilationUnit astRoot,
        ClassObject entity,
        RelationshipInfo relationship,
        ASTRewrite rewriter,
        ImportRewrite importRewrite,
        AST ast
    ) {
        String targetEntityName = relationship.getToEntity();
        String simpleTargetEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(targetEntityName);
        String syncingSetClassName = "Syncing" + simpleTargetEntityName + "Set";
        String elementCollectionFieldName = relationship.getJoinTableInverseJoinColumns() + "s";

        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(systemObject);
        String targetIdFieldName = jpaExtractor.extractIdFieldName(targetEntityName);
        String idAccessMethod = findIdGetterName(targetEntityName, targetIdFieldName).orElse(targetIdFieldName);

        importRewrite.addImport("java.util.AbstractSet");
        importRewrite.addImport("java.util.Collection");
        importRewrite.addImport("java.util.HashSet");
        importRewrite.addImport("java.util.Iterator");

        TypeDeclaration innerClass = ast.newTypeDeclaration();
        innerClass.setName(ast.newSimpleName(syncingSetClassName));
        innerClass.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
        innerClass.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

        ParameterizedType superClassType = ast.newParameterizedType(ast.newSimpleType(ast.newName("AbstractSet")));
        superClassType.typeArguments().add(ast.newSimpleType(ast.newName(simpleTargetEntityName)));
        innerClass.setSuperclassType(superClassType);

        VariableDeclarationFragment delegateFragment = ast.newVariableDeclarationFragment();
        delegateFragment.setName(ast.newSimpleName("delegate"));
        FieldDeclaration delegateField = ast.newFieldDeclaration(delegateFragment);
        ParameterizedType delegateType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Set")));
        delegateType.typeArguments().add(ast.newSimpleType(ast.newName(simpleTargetEntityName)));
        delegateField.setType(delegateType);
        delegateField.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
        delegateField.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));
        innerClass.bodyDeclarations().add(delegateField);

        innerClass.bodyDeclarations().add(createSyncingSetConstructor(ast, syncingSetClassName));
        innerClass.bodyDeclarations().add(createSizeMethod(ast));
        innerClass.bodyDeclarations().add(createContainsMethod(ast, simpleTargetEntityName));
        innerClass.bodyDeclarations().add(createIteratorMethod(ast, simpleTargetEntityName, elementCollectionFieldName, idAccessMethod));
        innerClass.bodyDeclarations().add(createAddMethod(ast, simpleTargetEntityName, elementCollectionFieldName, idAccessMethod));
        innerClass.bodyDeclarations().add(createRemoveMethod(ast, simpleTargetEntityName, elementCollectionFieldName, idAccessMethod));
        innerClass.bodyDeclarations().add(createClearMethod(ast, elementCollectionFieldName));
        innerClass.bodyDeclarations().add(createAddAllMethod(ast, simpleTargetEntityName));
        innerClass.bodyDeclarations().add(createRemoveAllMethod(ast));
        innerClass.bodyDeclarations().add(createRetainAllMethod(ast, simpleTargetEntityName));

        TypeDeclaration outerClass = (TypeDeclaration) astRoot.types().get(0);
        ListRewrite bodyRewrite = rewriter.getListRewrite(outerClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        bodyRewrite.insertLast(innerClass, new TextEditGroup("Add SyncingSet inner class"));
        return true;
    }

    // The id getter can be declared in a @MappedSuperclass, so we have to search the whole mapped hierarchy.
    private Optional<String> findIdGetterName(String entityName, String idFieldName) {
        String conventionalGetterName = "get" + UnmapJpaRelationshipsUtils.capitalize(idFieldName);

        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(systemObject);
        for (ClassObject classObj : jpaExtractor.getMappedHierarchy(entityName)) {
            if (declaresMethod(classObj, conventionalGetterName) || generatesLombokGetter(classObj, idFieldName)) {
                return Optional.of(conventionalGetterName);
            }
        }

        return Optional.empty();
    }

    private boolean declaresMethod(ClassObject classObj, String methodName) {
        Iterator<MethodObject> methodIterator = classObj.getMethodIterator();
        while (methodIterator.hasNext()) {
            MethodObject method = methodIterator.next();
            if (methodName.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }

    // Lombok generates the getters of the class it annotates at compile time, so they are absent
    // from the AST even though the generated code would be able to call them.
    private boolean generatesLombokGetter(ClassObject classObj, String fieldName) {
        if (!JpaAnnotationExtractorUtils.hasClassAnnotation(classObj, "Data") && !JpaAnnotationExtractorUtils.hasClassAnnotation(classObj, "Getter")) {
            return false;
        }
        return getFieldFromEntity(fieldName, classObj) != null;
    }

    private MethodDeclaration createSyncingSetConstructor(AST ast, String className) {
        MethodDeclaration constructor = ast.newMethodDeclaration();
        constructor.setConstructor(true);
        constructor.setName(ast.newSimpleName(className));

        Assignment assignment = ast.newAssignment();
        FieldAccess thisDelegate = ast.newFieldAccess();
        thisDelegate.setExpression(ast.newThisExpression());
        thisDelegate.setName(ast.newSimpleName("delegate"));
        assignment.setLeftHandSide(thisDelegate);

        ClassInstanceCreation newHashSet = ast.newClassInstanceCreation();
        ParameterizedType hashSetType = ast.newParameterizedType(ast.newSimpleType(ast.newName("HashSet")));
        newHashSet.setType(hashSetType);
        assignment.setRightHandSide(newHashSet);

        Block body = ast.newBlock();
        body.statements().add(ast.newExpressionStatement(assignment));
        constructor.setBody(body);
        return constructor;
    }

    private MethodDeclaration createSizeMethod(AST ast) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("size"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.INT));

        Block body = ast.newBlock();
        ReturnStatement returnStmt = ast.newReturnStatement();
        MethodInvocation sizeCall = ast.newMethodInvocation();
        sizeCall.setExpression(ast.newSimpleName("delegate"));
        sizeCall.setName(ast.newSimpleName("size"));
        returnStmt.setExpression(sizeCall);
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createContainsMethod(AST ast, String elementType) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("contains"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setType(ast.newSimpleType(ast.newName("Object")));
        param.setName(ast.newSimpleName("o"));
        method.parameters().add(param);

        Block body = ast.newBlock();
        ReturnStatement returnStmt = ast.newReturnStatement();
        MethodInvocation containsCall = ast.newMethodInvocation();
        containsCall.setExpression(ast.newSimpleName("delegate"));
        containsCall.setName(ast.newSimpleName("contains"));
        containsCall.arguments().add(ast.newSimpleName("o"));
        returnStmt.setExpression(containsCall);
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createIteratorMethod(AST ast, String elementType, String elementCollectionFieldName, String idAccessMethod) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("iterator"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

        ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Iterator")));
        iteratorType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));
        method.setReturnType2(iteratorType);

        Block body = ast.newBlock();
        MethodInvocation delegateIterator = ast.newMethodInvocation();
        delegateIterator.setExpression(ast.newSimpleName("delegate"));
        delegateIterator.setName(ast.newSimpleName("iterator"));
        VariableDeclarationFragment itFragment = ast.newVariableDeclarationFragment();
        itFragment.setName(ast.newSimpleName("it"));
        itFragment.setInitializer(delegateIterator);
        VariableDeclarationStatement itDecl = ast.newVariableDeclarationStatement(itFragment);
        itDecl.setType((ParameterizedType) ASTNode.copySubtree(ast, iteratorType));
        body.statements().add(itDecl);

        AnonymousClassDeclaration anonymousClass = ast.newAnonymousClassDeclaration();
        VariableDeclarationFragment currentFragment = ast.newVariableDeclarationFragment();
        currentFragment.setName(ast.newSimpleName("current"));
        FieldDeclaration currentField = ast.newFieldDeclaration(currentFragment);
        currentField.setType(ast.newSimpleType(ast.newName(elementType)));
        anonymousClass.bodyDeclarations().add(currentField);
        anonymousClass.bodyDeclarations().add(createIteratorHasNextMethod(ast));
        anonymousClass.bodyDeclarations().add(createIteratorNextMethod(ast, elementType));
        anonymousClass.bodyDeclarations().add(createIteratorRemoveMethod(ast, elementCollectionFieldName, idAccessMethod));

        ClassInstanceCreation anonymousIterator = ast.newClassInstanceCreation();
        anonymousIterator.setType((ParameterizedType) ASTNode.copySubtree(ast, iteratorType));
        anonymousIterator.setAnonymousClassDeclaration(anonymousClass);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(anonymousIterator);
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createIteratorHasNextMethod(AST ast) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("hasNext"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        Block body = ast.newBlock();
        ReturnStatement returnStmt = ast.newReturnStatement();
        MethodInvocation hasNextCall = ast.newMethodInvocation();
        hasNextCall.setExpression(ast.newSimpleName("it"));
        hasNextCall.setName(ast.newSimpleName("hasNext"));
        returnStmt.setExpression(hasNextCall);
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createIteratorNextMethod(AST ast, String elementType) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("next"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newSimpleType(ast.newName(elementType)));

        Block body = ast.newBlock();
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide(ast.newSimpleName("current"));
        MethodInvocation nextCall = ast.newMethodInvocation();
        nextCall.setExpression(ast.newSimpleName("it"));
        nextCall.setName(ast.newSimpleName("next"));
        assignment.setRightHandSide(nextCall);
        body.statements().add(ast.newExpressionStatement(assignment));

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("current"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createIteratorRemoveMethod(AST ast, String elementCollectionFieldName, String idAccessMethod) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("remove"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

        Block body = ast.newBlock();
        MethodInvocation itRemoveCall = ast.newMethodInvocation();
        itRemoveCall.setExpression(ast.newSimpleName("it"));
        itRemoveCall.setName(ast.newSimpleName("remove"));
        body.statements().add(ast.newExpressionStatement(itRemoveCall));

        InfixExpression currentNotNull = ast.newInfixExpression();
        currentNotNull.setLeftOperand(ast.newSimpleName("current"));
        currentNotNull.setOperator(InfixExpression.Operator.NOT_EQUALS);
        currentNotNull.setRightOperand(ast.newNullLiteral());

        MethodInvocation getCurrentId = ast.newMethodInvocation();
        getCurrentId.setExpression(ast.newSimpleName("current"));
        getCurrentId.setName(ast.newSimpleName(idAccessMethod));

        InfixExpression idNotNull = ast.newInfixExpression();
        idNotNull.setLeftOperand(getCurrentId);
        idNotNull.setOperator(InfixExpression.Operator.NOT_EQUALS);
        idNotNull.setRightOperand(ast.newNullLiteral());

        InfixExpression combined = ast.newInfixExpression();
        combined.setLeftOperand(currentNotNull);
        combined.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        combined.setRightOperand(idNotNull);

        MethodInvocation removeFromElementCollection = ast.newMethodInvocation();
        removeFromElementCollection.setExpression(ast.newSimpleName(elementCollectionFieldName));
        removeFromElementCollection.setName(ast.newSimpleName("remove"));

        MethodInvocation getCurrentId2 = ast.newMethodInvocation();
        getCurrentId2.setExpression(ast.newSimpleName("current"));
		getCurrentId2.setName(ast.newSimpleName(idAccessMethod));
        removeFromElementCollection.arguments().add(getCurrentId2);

        Block thenBlock = ast.newBlock();
        thenBlock.statements().add(ast.newExpressionStatement(removeFromElementCollection));

        IfStatement ifStmt = ast.newIfStatement();
        ifStmt.setExpression(combined);
        ifStmt.setThenStatement(thenBlock);
        body.statements().add(ifStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createAddMethod(AST ast, String elementType, String elementCollectionFieldName, String idAccessMethod) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("add"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setType(ast.newSimpleType(ast.newName(elementType)));
        param.setName(ast.newSimpleName("e"));
        method.parameters().add(param);

        Block body = ast.newBlock();

        InfixExpression nullCheck = ast.newInfixExpression();
        nullCheck.setLeftOperand(ast.newSimpleName("e"));
        nullCheck.setOperator(InfixExpression.Operator.EQUALS);
        nullCheck.setRightOperand(ast.newNullLiteral());

        Block nullThenBlock = ast.newBlock();
        ReturnStatement returnFalse1 = ast.newReturnStatement();
        returnFalse1.setExpression(ast.newBooleanLiteral(false));
        nullThenBlock.statements().add(returnFalse1);

        IfStatement nullIf = ast.newIfStatement();
        nullIf.setExpression(nullCheck);
        nullIf.setThenStatement(nullThenBlock);
        body.statements().add(nullIf);

        MethodInvocation getId = ast.newMethodInvocation();
        getId.setExpression(ast.newSimpleName("e"));
        getId.setName(ast.newSimpleName(idAccessMethod));

        InfixExpression idNullCheck = ast.newInfixExpression();
        idNullCheck.setLeftOperand(getId);
        idNullCheck.setOperator(InfixExpression.Operator.EQUALS);
        idNullCheck.setRightOperand(ast.newNullLiteral());

        Block idNullThenBlock = ast.newBlock();
        ReturnStatement returnFalse2 = ast.newReturnStatement();
        returnFalse2.setExpression(ast.newBooleanLiteral(false));
        idNullThenBlock.statements().add(returnFalse2);

        IfStatement idNullIf = ast.newIfStatement();
        idNullIf.setExpression(idNullCheck);
        idNullIf.setThenStatement(idNullThenBlock);
        body.statements().add(idNullIf);

        VariableDeclarationFragment changedFragment = ast.newVariableDeclarationFragment();
        changedFragment.setName(ast.newSimpleName("changed"));
        MethodInvocation delegateAdd = ast.newMethodInvocation();
        delegateAdd.setExpression(ast.newSimpleName("delegate"));
        delegateAdd.setName(ast.newSimpleName("add"));
        delegateAdd.arguments().add(ast.newSimpleName("e"));
        changedFragment.setInitializer(delegateAdd);
        VariableDeclarationStatement changedDecl = ast.newVariableDeclarationStatement(changedFragment);
        changedDecl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        body.statements().add(changedDecl);

        MethodInvocation addToElementCollection = ast.newMethodInvocation();
        addToElementCollection.setExpression(ast.newSimpleName(elementCollectionFieldName));
        addToElementCollection.setName(ast.newSimpleName("add"));

        MethodInvocation getId2 = ast.newMethodInvocation();
        getId2.setExpression(ast.newSimpleName("e"));
        getId2.setName(ast.newSimpleName(idAccessMethod));
        addToElementCollection.arguments().add(getId2);

        Block changedThenBlock = ast.newBlock();
        changedThenBlock.statements().add(ast.newExpressionStatement(addToElementCollection));

        IfStatement changedIf = ast.newIfStatement();
        changedIf.setExpression(ast.newSimpleName("changed"));
        changedIf.setThenStatement(changedThenBlock);
        body.statements().add(changedIf);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("changed"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createRemoveMethod(AST ast, String elementType, String elementCollectionFieldName, String idAccessMethod) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("remove"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setType(ast.newSimpleType(ast.newName("Object")));
        param.setName(ast.newSimpleName("o"));
        method.parameters().add(param);

        Block body = ast.newBlock();
        InstanceofExpression instanceofExpr = ast.newInstanceofExpression();
        instanceofExpr.setLeftOperand(ast.newSimpleName("o"));
        instanceofExpr.setRightOperand(ast.newSimpleType(ast.newName(elementType)));

        PrefixExpression notInstanceof = ast.newPrefixExpression();
        notInstanceof.setOperator(PrefixExpression.Operator.NOT);
        ParenthesizedExpression parenInstanceof = ast.newParenthesizedExpression();
        parenInstanceof.setExpression(instanceofExpr);
        notInstanceof.setOperand(parenInstanceof);

        Block notInstanceofThenBlock = ast.newBlock();
        ReturnStatement returnFalse1 = ast.newReturnStatement();
        returnFalse1.setExpression(ast.newBooleanLiteral(false));
        notInstanceofThenBlock.statements().add(returnFalse1);

        IfStatement notInstanceofIf = ast.newIfStatement();
        notInstanceofIf.setExpression(notInstanceof);
        notInstanceofIf.setThenStatement(notInstanceofThenBlock);
        body.statements().add(notInstanceofIf);

        VariableDeclarationFragment aFragment = ast.newVariableDeclarationFragment();
        aFragment.setName(ast.newSimpleName("e"));
        CastExpression cast = ast.newCastExpression();
        cast.setType(ast.newSimpleType(ast.newName(elementType)));
        cast.setExpression(ast.newSimpleName("o"));
        aFragment.setInitializer(cast);
        VariableDeclarationStatement aDecl = ast.newVariableDeclarationStatement(aFragment);
        aDecl.setType(ast.newSimpleType(ast.newName(elementType)));
        body.statements().add(aDecl);

        MethodInvocation getId = ast.newMethodInvocation();
        getId.setExpression(ast.newSimpleName("e"));
        getId.setName(ast.newSimpleName(idAccessMethod));

        InfixExpression idNullCheck = ast.newInfixExpression();
        idNullCheck.setLeftOperand(getId);
        idNullCheck.setOperator(InfixExpression.Operator.EQUALS);
        idNullCheck.setRightOperand(ast.newNullLiteral());

        Block idNullThenBlock = ast.newBlock();
        ReturnStatement returnFalse2 = ast.newReturnStatement();
        returnFalse2.setExpression(ast.newBooleanLiteral(false));
        idNullThenBlock.statements().add(returnFalse2);

        IfStatement idNullIf = ast.newIfStatement();
        idNullIf.setExpression(idNullCheck);
        idNullIf.setThenStatement(idNullThenBlock);
        body.statements().add(idNullIf);

        VariableDeclarationFragment changedFragment = ast.newVariableDeclarationFragment();
        changedFragment.setName(ast.newSimpleName("changed"));
        MethodInvocation delegateRemove = ast.newMethodInvocation();
        delegateRemove.setExpression(ast.newSimpleName("delegate"));
        delegateRemove.setName(ast.newSimpleName("remove"));
        delegateRemove.arguments().add(ast.newSimpleName("o"));
        changedFragment.setInitializer(delegateRemove);
        VariableDeclarationStatement changedDecl = ast.newVariableDeclarationStatement(changedFragment);
        changedDecl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        body.statements().add(changedDecl);

        MethodInvocation removeFromElementCollection = ast.newMethodInvocation();
        removeFromElementCollection.setExpression(ast.newSimpleName(elementCollectionFieldName));
        removeFromElementCollection.setName(ast.newSimpleName("remove"));

        MethodInvocation getId2 = ast.newMethodInvocation();
        getId2.setExpression(ast.newSimpleName("e"));
        getId2.setName(ast.newSimpleName(idAccessMethod));
        removeFromElementCollection.arguments().add(getId2);

        Block changedThenBlock = ast.newBlock();
        changedThenBlock.statements().add(ast.newExpressionStatement(removeFromElementCollection));

        IfStatement changedIf = ast.newIfStatement();
        changedIf.setExpression(ast.newSimpleName("changed"));
        changedIf.setThenStatement(changedThenBlock);
        body.statements().add(changedIf);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("changed"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createClearMethod(AST ast, String elementCollectionFieldName) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("clear"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

        Block body = ast.newBlock();
        MethodInvocation delegateClear = ast.newMethodInvocation();
        delegateClear.setExpression(ast.newSimpleName("delegate"));
        delegateClear.setName(ast.newSimpleName("clear"));
        body.statements().add(ast.newExpressionStatement(delegateClear));

        MethodInvocation elementCollectionClear = ast.newMethodInvocation();
        elementCollectionClear.setExpression(ast.newSimpleName(elementCollectionFieldName));
        elementCollectionClear.setName(ast.newSimpleName("clear"));
        body.statements().add(ast.newExpressionStatement(elementCollectionClear));
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createAddAllMethod(AST ast, String elementType) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("addAll"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        ParameterizedType collectionType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Collection")));
        WildcardType wildcardType = ast.newWildcardType();
        wildcardType.setBound(ast.newSimpleType(ast.newName(elementType)), true);
        collectionType.typeArguments().add(wildcardType);
        param.setType(collectionType);
        param.setName(ast.newSimpleName("c"));
        method.parameters().add(param);

        Block body = ast.newBlock();
        VariableDeclarationFragment changedFragment = ast.newVariableDeclarationFragment();
        changedFragment.setName(ast.newSimpleName("changed"));
        changedFragment.setInitializer(ast.newBooleanLiteral(false));
        VariableDeclarationStatement changedDecl = ast.newVariableDeclarationStatement(changedFragment);
        changedDecl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        body.statements().add(changedDecl);

        EnhancedForStatement forLoop = ast.newEnhancedForStatement();
        SingleVariableDeclaration forParam = ast.newSingleVariableDeclaration();
        forParam.setType(ast.newSimpleType(ast.newName(elementType)));
        forParam.setName(ast.newSimpleName("e"));
        forLoop.setParameter(forParam);
        forLoop.setExpression(ast.newSimpleName("c"));

        Block forBody = ast.newBlock();
        Assignment orAssignment = ast.newAssignment();
        orAssignment.setLeftHandSide(ast.newSimpleName("changed"));
        orAssignment.setOperator(Assignment.Operator.BIT_OR_ASSIGN);
        MethodInvocation addCall = ast.newMethodInvocation();
        addCall.setName(ast.newSimpleName("add"));
        addCall.arguments().add(ast.newSimpleName("e"));
        orAssignment.setRightHandSide(addCall);
        forBody.statements().add(ast.newExpressionStatement(orAssignment));
        forLoop.setBody(forBody);
        body.statements().add(forLoop);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("changed"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createRemoveAllMethod(AST ast) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("removeAll"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        ParameterizedType collectionType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Collection")));
        WildcardType wildcardType = ast.newWildcardType();
        collectionType.typeArguments().add(wildcardType);
        param.setType(collectionType);
        param.setName(ast.newSimpleName("c"));
        method.parameters().add(param);

        Block body = ast.newBlock();
        VariableDeclarationFragment changedFragment = ast.newVariableDeclarationFragment();
        changedFragment.setName(ast.newSimpleName("changed"));
        changedFragment.setInitializer(ast.newBooleanLiteral(false));
        VariableDeclarationStatement changedDecl = ast.newVariableDeclarationStatement(changedFragment);
        changedDecl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        body.statements().add(changedDecl);

        EnhancedForStatement forLoop = ast.newEnhancedForStatement();
        SingleVariableDeclaration forParam = ast.newSingleVariableDeclaration();
        forParam.setType(ast.newSimpleType(ast.newName("Object")));
        forParam.setName(ast.newSimpleName("o"));
        forLoop.setParameter(forParam);
        forLoop.setExpression(ast.newSimpleName("c"));

        Block forBody = ast.newBlock();
        Assignment orAssignment = ast.newAssignment();
        orAssignment.setLeftHandSide(ast.newSimpleName("changed"));
        orAssignment.setOperator(Assignment.Operator.BIT_OR_ASSIGN);
        MethodInvocation removeCall = ast.newMethodInvocation();
        removeCall.setName(ast.newSimpleName("remove"));
        removeCall.arguments().add(ast.newSimpleName("o"));
        orAssignment.setRightHandSide(removeCall);
        forBody.statements().add(ast.newExpressionStatement(orAssignment));
        forLoop.setBody(forBody);
        body.statements().add(forLoop);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("changed"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }

    private MethodDeclaration createRetainAllMethod(AST ast, String elementType) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setName(ast.newSimpleName("retainAll"));

        MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
        overrideAnnotation.setTypeName(ast.newName("Override"));
        method.modifiers().add(overrideAnnotation);
        method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
        method.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));

        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        ParameterizedType collectionType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Collection")));
        WildcardType wildcardType = ast.newWildcardType();
        collectionType.typeArguments().add(wildcardType);
        param.setType(collectionType);
        param.setName(ast.newSimpleName("c"));
        method.parameters().add(param);

        Block body = ast.newBlock();
        VariableDeclarationFragment changedFragment = ast.newVariableDeclarationFragment();
        changedFragment.setName(ast.newSimpleName("changed"));
        changedFragment.setInitializer(ast.newBooleanLiteral(false));
        VariableDeclarationStatement changedDecl = ast.newVariableDeclarationStatement(changedFragment);
        changedDecl.setType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
        body.statements().add(changedDecl);

        VariableDeclarationFragment itFragment = ast.newVariableDeclarationFragment();
        itFragment.setName(ast.newSimpleName("it"));
        MethodInvocation iteratorCall = ast.newMethodInvocation();
        iteratorCall.setName(ast.newSimpleName("iterator"));
        itFragment.setInitializer(iteratorCall);
        VariableDeclarationStatement itDecl = ast.newVariableDeclarationStatement(itFragment);
        ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Iterator")));
        iteratorType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));
        itDecl.setType(iteratorType);
        body.statements().add(itDecl);

        WhileStatement whileLoop = ast.newWhileStatement();
        MethodInvocation hasNextCall = ast.newMethodInvocation();
        hasNextCall.setExpression(ast.newSimpleName("it"));
        hasNextCall.setName(ast.newSimpleName("hasNext"));
        whileLoop.setExpression(hasNextCall);

        Block whileBody = ast.newBlock();
        VariableDeclarationFragment aFragment = ast.newVariableDeclarationFragment();
        aFragment.setName(ast.newSimpleName("e"));
        MethodInvocation nextCall = ast.newMethodInvocation();
        nextCall.setExpression(ast.newSimpleName("it"));
        nextCall.setName(ast.newSimpleName("next"));
        aFragment.setInitializer(nextCall);
        VariableDeclarationStatement aDecl = ast.newVariableDeclarationStatement(aFragment);
        aDecl.setType(ast.newSimpleType(ast.newName(elementType)));
        whileBody.statements().add(aDecl);

        MethodInvocation containsCall = ast.newMethodInvocation();
        containsCall.setExpression(ast.newSimpleName("c"));
        containsCall.setName(ast.newSimpleName("contains"));
        containsCall.arguments().add(ast.newSimpleName("e"));

        PrefixExpression notContains = ast.newPrefixExpression();
        notContains.setOperator(PrefixExpression.Operator.NOT);
        notContains.setOperand(containsCall);

        IfStatement ifStmt = ast.newIfStatement();
        ifStmt.setExpression(notContains);

        Block thenBlock = ast.newBlock();
        MethodInvocation itRemoveCall = ast.newMethodInvocation();
        itRemoveCall.setExpression(ast.newSimpleName("it"));
        itRemoveCall.setName(ast.newSimpleName("remove"));
        thenBlock.statements().add(ast.newExpressionStatement(itRemoveCall));

        Assignment changedAssignment = ast.newAssignment();
        changedAssignment.setLeftHandSide(ast.newSimpleName("changed"));
        changedAssignment.setRightHandSide(ast.newBooleanLiteral(true));
        thenBlock.statements().add(ast.newExpressionStatement(changedAssignment));

        ifStmt.setThenStatement(thenBlock);
        whileBody.statements().add(ifStmt);
        whileLoop.setBody(whileBody);
        body.statements().add(whileLoop);

        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression(ast.newSimpleName("changed"));
        body.statements().add(returnStmt);
        method.setBody(body);
        return method;
    }
}