package gr.aueb.java.archifactor.modules.identification;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.search.*;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import java.util.*;


public abstract class BaseCallGraphBuilder {
    protected final IJavaProject javaProject;
    protected final SystemObject systemObject;
    protected final Set<String> projectSourcePackages;
    private final Map<String, IMethod> implementationCache = new HashMap<String, IMethod>();

    public BaseCallGraphBuilder(IJavaProject javaProject, SystemObject systemObject) throws JavaModelException {
        this.javaProject = javaProject;
        this.systemObject = systemObject;
        this.projectSourcePackages = determineProjectSourcePackages(javaProject.getPackageFragments());
    }

    protected abstract boolean isController(TypeDeclaration node);
    protected abstract boolean isEndpoint(MethodDeclaration method);
    protected abstract boolean isScheduledJobMethod(MethodDeclaration method);
    protected abstract boolean isTransactional(MethodDeclaration method);
    protected abstract boolean isTransactional(TypeDeclaration type);
    protected abstract ITypeBinding resolveFrameworkPersistedEntityType(MethodInvocation invocation, IMethodBinding binding);
    protected abstract IType selectPrimaryImplementation(List<IType> candidates);

    public List<CallGraph> buildCallGraphs(IProgressMonitor monitor) throws JavaModelException {
        List<CallGraph> callGraphs = new ArrayList<CallGraph>();

        List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();
        for (IPackageFragment packageFragment : javaProject.getPackageFragments()) {
            if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                for (ICompilationUnit cu : packageFragment.getCompilationUnits()) {
                    compilationUnits.add(cu);
                }
            }
        }

        monitor.beginTask("Building endpoint call graphs", compilationUnits.size());
        for (ICompilationUnit cu : compilationUnits) {
            if (monitor.isCanceled()) {
                throw new OperationCanceledException();
            }
            monitor.subTask("Analyzing " + cu.getElementName());

            ASTParser parser = ASTParser.newParser(ASTReader.JLS);
            parser.setSource(cu);
            parser.setResolveBindings(true);
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

            astRoot.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    boolean isController = isController(node);

                    for (MethodDeclaration method : node.getMethods()) {
                        if (!(isController && isEndpoint(method)) && !isScheduledJobMethod(method)) {
                            continue;
                        }
                        
                        IMethodBinding methodBinding = method.resolveBinding();
                        if (methodBinding == null) {
                            continue;
                        }

                        ITypeBinding rootClass = methodBinding.getDeclaringClass();
                        if (rootClass == null) {
                            continue;
                        }

                        String rootClassFqn = rootClass.getQualifiedName();
                        ClassObject rootClassObject = systemObject.getClassObject(rootClassFqn);

                        String methodName = node.getName().getIdentifier() + "." + method.getName().getIdentifier();
                        CallGraphNode rootNode = new CallGraphNode(methodName);
                        rootNode.classObject = rootClassObject;
                        rootNode.isTransactional = isTransactional(node, method, null);
                        
                        CallGraph callGraph = new CallGraph();
                        callGraph.addNode(rootNode);

                        findMethodCalls(rootNode, method, new HashSet<String>());
                        callGraphs.add(callGraph);
                    }

                    return super.visit(node);
                }
            });

            monitor.worked(1);
        }

        return callGraphs;
    }

    private void findMethodCalls(final CallGraphNode parentNode, final MethodDeclaration method, final Set<String> visitedMethods) {
        method.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                handleCollectionMutation(parentNode, methodInvocation);
                handleMethodInvocation(parentNode, methodInvocation, visitedMethods);
                return super.visit(methodInvocation);
            }

            @Override
            public boolean visit(ClassInstanceCreation node) {
                handleInstanceCreation(parentNode, node);
                return super.visit(node);
            }

            @Override
            public boolean visit(Assignment node) {
                handleEntityFieldWrite(parentNode, node.getLeftHandSide());
                return super.visit(node);
            }

            @Override
            public boolean visit(PrefixExpression node) {
                PrefixExpression.Operator operator = node.getOperator();
                if (operator == PrefixExpression.Operator.INCREMENT || operator == PrefixExpression.Operator.DECREMENT) {
                    handleEntityFieldWrite(parentNode, node.getOperand());
                }
                return super.visit(node);
            }

            @Override
            public boolean visit(PostfixExpression node) {
                handleEntityFieldWrite(parentNode, node.getOperand());
                return super.visit(node);
            }
        });
    }

    private void handleMethodInvocation(CallGraphNode parentNode, MethodInvocation methodInvocation, Set<String> visitedMethods) {
        IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
        if (methodBinding == null) {
            return;
        }

        ITypeBinding invokedMethodType = methodBinding.getDeclaringClass();
        if (invokedMethodType == null) {
            return;
        }

        ITypeBinding persistedEntityType = resolvePersistedEntityType(methodInvocation, methodBinding);
        String invokedMethodTypeFqn = invokedMethodType.getQualifiedName();
        boolean userWritten = isProjectSource(invokedMethodTypeFqn);
        if (persistedEntityType == null && !userWritten) {
            return;
        }

        // Key by binding signature: a name-only key collapses overloads and truncates self-delegating overload chains.
        if (!visitedMethods.add(methodBinding.getMethodDeclaration().getKey())) {
            return;
        }

        String fullMethodName = invokedMethodTypeFqn + "." + methodInvocation.getName().getIdentifier();
        if (persistedEntityType != null) {
            handlePersistenceCall(parentNode, fullMethodName, persistedEntityType, isDeleteMethodName(methodBinding.getName()));
        } else {
        	handleUserWrittenCall(parentNode, methodBinding, invokedMethodTypeFqn, fullMethodName, visitedMethods);
        }
    }

    private final ITypeBinding resolvePersistedEntityType(MethodInvocation methodInvocation, IMethodBinding methodBinding) {
        ITypeBinding invokedMethodType = methodBinding.getDeclaringClass();
        if (invokedMethodType == null || invokedMethodType.getErasure() == null) {
            return null;
        }

        String invokedMethodTypeFqn = invokedMethodType.getErasure().getQualifiedName();
        String invokedMethodName = methodBinding.getName();

        boolean isEntityManager = invokedMethodTypeFqn.equals("jakarta.persistence.EntityManager") || invokedMethodTypeFqn.equals("javax.persistence.EntityManager");
        boolean isPersistenceCall = invokedMethodName.equals("persist") || invokedMethodName.equals("merge") || invokedMethodName.equals("remove");
        if (isEntityManager && isPersistenceCall) {
            return extractEntityTypeFromFirstArgument(methodInvocation);
        }

        return resolveFrameworkPersistedEntityType(methodInvocation, methodBinding);
    }

    private final ITypeBinding extractEntityTypeFromFirstArgument(MethodInvocation invocation) {
        List<?> arguments = invocation.arguments();
        if (arguments.isEmpty()) {
            return null;
        }

        Expression firstArg = (Expression) arguments.get(0);
        return firstArg.resolveTypeBinding();
    }

    private void handlePersistenceCall(CallGraphNode parentNode, String fullMethodName, ITypeBinding persistedEntityType, boolean isDelete) {
        CallGraphNode calledNode = new CallGraphNode(fullMethodName);

        ClassObject entityClass = systemObject.getClassObject(persistedEntityType.getQualifiedName());
        if (entityClass == null) {
            parentNode.calledMethods.add(calledNode);
            return;
        }

        if (isDelete) {
            calledNode.deletedEntities.add(entityClass.getName());
            calledNode.deletedEntitiesObjects.add(entityClass);
        } else {
            calledNode.createdEntities.add(entityClass.getName());
            calledNode.createdEntitiesObjects.add(entityClass);
        }

        parentNode.calledMethods.add(calledNode);
        parentNode.createdEntities.addAll(calledNode.createdEntities);
        parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
        parentNode.deletedEntities.addAll(calledNode.deletedEntities);
        parentNode.deletedEntitiesObjects.addAll(calledNode.deletedEntitiesObjects);
    }

    private static boolean isDeleteMethodName(String methodName) {
        return methodName.startsWith("delete") || methodName.startsWith("remove");
    }

    private void handleUserWrittenCall(CallGraphNode parentNode, IMethodBinding methodBinding, String invokedMethodTypeName, String fullMethodName, Set<String> visitedMethods) {
        CallGraphNode calledNode = new CallGraphNode(fullMethodName);
        ClassObject invokedMethodClass = systemObject.getClassObject(invokedMethodTypeName);
        calledNode.classObject = invokedMethodClass;

        MethodObject calleeMethod = resolveCalleeMethod(methodBinding);
        if (calleeMethod == null) {
            parentNode.calledMethods.add(calledNode);
            return;
        }

        if (invokedMethodClass == null || invokedMethodClass.isInterface()) {
            calledNode.classObject = systemObject.getClassObject(calleeMethod.getClassName());
        }
        parentNode.calledMethods.add(calledNode);

        MethodDeclaration calleeMethodDeclaration = calleeMethod.getMethodDeclaration();
        if (calleeMethodDeclaration == null) {
            return;
        }

        calledNode.isTransactional = resolveTransactional(parentNode, calleeMethodDeclaration);
        findMethodCalls(calledNode, calleeMethodDeclaration, visitedMethods);
        mergeCalledNodeIntoParent(parentNode, calledNode);
    }

    private MethodObject resolveCalleeMethod(IMethodBinding methodBinding) {
        try {
            IMethod implMethod = resolveImplementation(methodBinding);
            if (implMethod == null) {
                return null;
            }
            return (MethodObject) systemObject.getMethodObject(implMethod);
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean resolveTransactional(CallGraphNode parentNode, MethodDeclaration calleeMethodDeclaration) {
        ASTNode parent = calleeMethodDeclaration.getParent();
        if (parent instanceof TypeDeclaration) {
            return isTransactional((TypeDeclaration) parent, calleeMethodDeclaration, parentNode);
        }
        return parentNode.isTransactional || isTransactional(calleeMethodDeclaration);
    }

    private void mergeCalledNodeIntoParent(CallGraphNode parentNode, CallGraphNode calledNode) {
        parentNode.definedEntities.addAll(calledNode.definedEntities);
        parentNode.definedEntitiesObjects.addAll(calledNode.definedEntitiesObjects);
        parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
        parentNode.createdEntities.addAll(calledNode.createdEntities);
        parentNode.deletedEntities.addAll(calledNode.deletedEntities);
        parentNode.deletedEntitiesObjects.addAll(calledNode.deletedEntitiesObjects);
        parentNode.creationRecords.addAll(calledNode.creationRecords);
    }

    private void handleInstanceCreation(CallGraphNode parentNode, ClassInstanceCreation node) {
        IMethodBinding constructorBinding = node.resolveConstructorBinding();
        if (constructorBinding == null) {
            return;
        }

        ITypeBinding instantiatedType = constructorBinding.getDeclaringClass();
        if (instantiatedType == null) {
            return;
        }

        if (!isEntityType(instantiatedType)) {
            return;
        }

        ClassObject classCreated = systemObject.getClassObject(instantiatedType.getQualifiedName());
        if (classCreated == null) {
            return;
        }

        parentNode.createdEntities.add(instantiatedType.getQualifiedName());
        parentNode.createdEntitiesObjects.add(classCreated);

        ITypeBinding parentType = parentNode.classObject != null
                ? parentNode.classObject.getAbstractTypeDeclaration().resolveBinding()
                : null;
        if (parentType != null && isEntityType(parentType)) {
            CreationRecord creation = new CreationRecord(classCreated, parentNode.classObject);
            parentNode.creationRecords.add(creation);
        }
    }

    private final boolean isEntityType(ITypeBinding type) {
        if (type == null) {
            return false;
        }

        for (IAnnotationBinding annotation : type.getAnnotations()) {
            if (annotation == null || annotation.getAnnotationType() == null) {
                continue;
            }

            String annotationFqn = annotation.getAnnotationType().getQualifiedName();
            if (annotationFqn.equals("jakarta.persistence.Entity") || annotationFqn.equals("javax.persistence.Entity")) {
                return true;
            }
        }

        return false;
    }

    private IMethod resolveImplementation(IMethodBinding methodBinding) throws JavaModelException {
        String cacheKey = methodBinding.getKey();
        if (implementationCache.containsKey(cacheKey)) {
            return implementationCache.get(cacheKey);
        }

        ITypeBinding invokedMethodType = methodBinding.getDeclaringClass();
        if (invokedMethodType == null) {
            implementationCache.put(cacheKey, null);
            return null;
        }

        if (!invokedMethodType.isInterface()) {
            IJavaElement javaElement = methodBinding.getJavaElement();
            IMethod result = javaElement instanceof IMethod ? (IMethod) javaElement : null;
            implementationCache.put(cacheKey, result);
            return result;
        }

        IType interfaceType = javaProject.findType(invokedMethodType.getQualifiedName());
        if (interfaceType == null) {
            implementationCache.put(cacheKey, null);
            return null;
        }

        List<IType> candidates = findSourceImplementers(interfaceType);
        if (candidates.isEmpty()) {
            implementationCache.put(cacheKey, null);
            return null;
        }

        IType selected = candidates.size() == 1 ? candidates.get(0) : selectPrimaryImplementation(candidates);
        if (selected == null) {
            System.out.println("Multiple implementations of " + interfaceType.getFullyQualifiedName() + " with no primary; Skipping...");
            implementationCache.put(cacheKey, null);
            return null;
        }

        IMethod match = findMatchingMethod(selected, methodBinding);
        implementationCache.put(cacheKey, match);
        return match;
    }

    private List<IType> findSourceImplementers(IType interfaceType) throws JavaModelException {
        List<IType> implementers = new ArrayList<IType>();
        SearchPattern pattern = SearchPattern.createPattern(interfaceType, IJavaSearchConstants.IMPLEMENTORS);
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { javaProject });
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                Object element = match.getElement();
                if (!(element instanceof IType)) {
                    return;
                }
                IType type = (IType) element;
                if (type.isInterface()) {
                    return;
                }
                if (type.getCompilationUnit() == null) {
                    return;
                }
                if (!isProjectSource(type.getPackageFragment().getElementName())) {
                    return;
                }
                implementers.add(type);
            }
        };

        try {
            new SearchEngine().search(
        		pattern, 
        		new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, 
        		scope, 
        		requestor, 
        		null
        	);
        } catch (CoreException e) {
            e.printStackTrace();
        }

        return implementers;
    }

    private IMethod findMatchingMethod(IType type, IMethodBinding binding) throws JavaModelException {
        String targetName = binding.getName();
        ITypeBinding[] targetParamTypes = binding.getParameterTypes();

        ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
        IType currentType = type;
        while (currentType != null) {
            for (IMethod method : currentType.getMethods()) {
                if (!method.getElementName().equals(targetName)) {
                    continue;
                }

                if (!parameterTypesMatch(method, targetParamTypes)) {
                    continue;
                }

                return method;
            }
            currentType = hierarchy.getSuperclass(currentType);
        }
        return null;
    }

    private boolean parameterTypesMatch(IMethod method, ITypeBinding[] targetParamTypes) {
        String[] methodParamSignatures = method.getParameterTypes();
        if (methodParamSignatures.length != targetParamTypes.length) {
            return false;
        }

        for (int i = 0; i < targetParamTypes.length; i++) {
            String methodParamSimpleName = Signature.getSignatureSimpleName(Signature.getTypeErasure(methodParamSignatures[i]));
            String targetParamSimpleName = targetParamTypes[i].getErasure().getName();
            if (!methodParamSimpleName.equals(targetParamSimpleName)) {
                return false;
            }
        }

        return true;
    }
    
    private void handleEntityFieldWrite(CallGraphNode parentNode, Expression writtenExpression) {
        // A field mutation only reaches the database via dirty checking, which requires an active transaction.
        if (!parentNode.isTransactional) {
            return;
        }

        IVariableBinding fieldBinding = extractFieldBinding(writtenExpression);
        if (fieldBinding == null || !fieldBinding.isField()) {
            return;
        }

        ITypeBinding fieldType = fieldBinding.getDeclaringClass();
        if (fieldType == null || !isEntityType(fieldType)) {
            return;
        }

        recordDefinedEntity(parentNode, fieldType);
    }

    private static final Set<String> COLLECTION_MUTATORS = new HashSet<String>(Arrays.asList(
        "add",
        "addAll",
        "remove",
        "removeAll",
        "retainAll",
        "clear",
        "put",
        "putAll"
    ));

    // Collection mutations persist through dirty checking like field assignments, so the same
    // transactional gate applies. Each captured mutation records both ends of the association.
    private void handleCollectionMutation(CallGraphNode parentNode, MethodInvocation methodInvocation) {
        if (!parentNode.isTransactional) {
            return;
        }

        if (!COLLECTION_MUTATORS.contains(methodInvocation.getName().getIdentifier())) {
            return;
        }

        Expression receiver = methodInvocation.getExpression();
        if (receiver == null) {
            return;
        }

        ITypeBinding mutatedEntityType = resolveMutatedEntityType(receiver);
        if (mutatedEntityType == null) {
            return;
        }

        recordDefinedEntity(parentNode, mutatedEntityType);
        recordEntityTypeArguments(parentNode, receiver.resolveTypeBinding());
    }

    // Decides whether the mutation deserves recording at all. Only collections owned by an
    // entity qualify, found through the declaring class of the field (this.items.add(x))
    // or of the accessor (entity.getItems().add(x)).
    private ITypeBinding resolveMutatedEntityType(Expression receiver) {
        IVariableBinding fieldBinding = extractFieldBinding(receiver);
        if (fieldBinding != null && fieldBinding.isField()) {
            ITypeBinding declaringClass = fieldBinding.getDeclaringClass();
            if (declaringClass != null && isEntityType(declaringClass)) {
                return declaringClass;
            }
            return null;
        }

        if (receiver instanceof MethodInvocation) {
            IMethodBinding accessorBinding = ((MethodInvocation) receiver).resolveMethodBinding();
            if (accessorBinding == null) {
                return null;
            }
            ITypeBinding declaringClass = accessorBinding.getDeclaringClass();
            if (declaringClass != null && isEntityType(declaringClass)) {
                return declaringClass;
            }
        }

        return null;
    }

    private void recordDefinedEntity(CallGraphNode parentNode, ITypeBinding entityType) {
        String mutatedEntityFqn = entityType.getQualifiedName();
        ClassObject mutatedEntityClass = systemObject.getClassObject(mutatedEntityFqn);
        if (mutatedEntityClass == null) {
            return;
        }

        parentNode.definedEntities.add(mutatedEntityFqn);
        parentNode.definedEntitiesObjects.add(mutatedEntityClass);
    }

    // Records the element half of the co-written pair. Whichever way the association is mapped,
    // the mutation touches its other end, so entity type arguments count as written too.
    private void recordEntityTypeArguments(CallGraphNode parentNode, ITypeBinding collectionType) {
        if (collectionType == null) {
            return;
        }

        for (ITypeBinding typeArgument : collectionType.getTypeArguments()) {
            if (isEntityType(typeArgument)) {
                recordDefinedEntity(parentNode, typeArgument);
            }
        }
    }

    private IVariableBinding extractFieldBinding(Expression expression) {
        if (expression instanceof ParenthesizedExpression) {
            return extractFieldBinding(((ParenthesizedExpression) expression).getExpression());
        }
        if (expression instanceof FieldAccess) {
            return ((FieldAccess) expression).resolveFieldBinding();
        }
        if (expression instanceof SuperFieldAccess) {
            return ((SuperFieldAccess) expression).resolveFieldBinding();
        }
        if (expression instanceof QualifiedName) {
            IBinding binding = ((QualifiedName) expression).resolveBinding();
            return binding instanceof IVariableBinding ? (IVariableBinding) binding : null;
        }
        if (expression instanceof SimpleName) {
            IBinding binding = ((SimpleName) expression).resolveBinding();
            return binding instanceof IVariableBinding ? (IVariableBinding) binding : null;
        }
        return null;
    }

    protected final ITypeBinding extractEntityTypeFromRepositoryHierarchy(MethodInvocation invocation, String repositoryFqn, int genericIndex) {
        ITypeBinding receiverType = null;
        if (invocation.getExpression() != null) {
            receiverType = invocation.getExpression().resolveTypeBinding();
        }

        if (receiverType == null) {
            return null;
        }

        ITypeBinding parameterized = findParameterizedSupertype(receiverType, repositoryFqn);
        if (parameterized == null) {
            return null;
        }

        ITypeBinding[] typeArgs = parameterized.getTypeArguments();
        if (typeArgs == null || typeArgs.length <= genericIndex) {
            return null;
        }

        return typeArgs[genericIndex];
    }

    protected final boolean isSubtypeOf(ITypeBinding type, String fqn) {
        return findParameterizedSupertype(type, fqn) != null;
    }

    private ITypeBinding findParameterizedSupertype(ITypeBinding type, String fqn) {
        if (type == null) {
            return null;
        }

        if (type.getErasure().getQualifiedName().equals(fqn)) {
            return type;
        }

        ITypeBinding found = findParameterizedSupertype(type.getSuperclass(), fqn);
        if (found != null) {
            return found;
        }

        for (ITypeBinding iface : type.getInterfaces()) {
            found = findParameterizedSupertype(iface, fqn);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
    
    protected boolean hasAnyAnnotation(List<?> modifiers, Set<String> fqns) {
        for (Object modifier : modifiers) {
            if (!(modifier instanceof Annotation)) {
                continue;
            }
            Annotation annotation = (Annotation) modifier;
            String name = annotation.getTypeName().getFullyQualifiedName();
            if (fqns.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTransactional(TypeDeclaration type, MethodDeclaration method, CallGraphNode callerNode) {
        return (callerNode != null && callerNode.isTransactional) || isTransactional(type) || isTransactional(method);
    }

    private Set<String> determineProjectSourcePackages(IPackageFragment[] packageFragments) throws JavaModelException {
        Set<String> packageNames = new HashSet<String>();
        for (IPackageFragment packageFragment : packageFragments) {
            if (packageFragment.getKind() != IPackageFragmentRoot.K_SOURCE) {
                continue;
            }
            String name = packageFragment.getElementName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (packageFragment.getCompilationUnits().length == 0) {
                continue;
            }
            packageNames.add(name);
        }
        return packageNames;
    }

    private boolean isProjectSource(String qualifiedName) {
        String candidate = qualifiedName;
        while (candidate != null) {
            if (projectSourcePackages.contains(candidate)) {
                return true;
            }
            int lastDot = candidate.lastIndexOf('.');
            candidate = lastDot > 0 ? candidate.substring(0, lastDot) : null;
        }
        return false;
    }
}
