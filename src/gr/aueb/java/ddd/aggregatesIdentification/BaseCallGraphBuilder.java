package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.runtime.CoreException;
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
    protected abstract boolean isTransactional(MethodDeclaration method);
    protected abstract boolean isTransactional(TypeDeclaration type);
    protected abstract ITypeBinding resolveFrameworkPersistedEntityType(MethodInvocation invocation, IMethodBinding binding);
    protected abstract IType selectPrimaryImplementation(List<IType> candidates);

    public List<CallGraph> buildCallGraphs() throws JavaModelException {
        List<CallGraph> callGraphs = new ArrayList<CallGraph>();

        List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();
        for (IPackageFragment packageFragment : javaProject.getPackageFragments()) {
            if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                for (ICompilationUnit cu : packageFragment.getCompilationUnits()) {
                    compilationUnits.add(cu);
                }
            }
        }

        for (ICompilationUnit cu : compilationUnits) {
            ASTParser parser = ASTParser.newParser(ASTReader.JLS);
            parser.setSource(cu);
            parser.setResolveBindings(true);
            CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

            astRoot.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    if (!isController(node)) {
                        return super.visit(node);
                    }

                    for (MethodDeclaration method : node.getMethods()) {
                        if (!isEndpoint(method)) {
                            continue;
                        }
                        
                        IMethodBinding methodBinding = method.resolveBinding();
                        if (methodBinding == null) {
                            continue;
                        }

                        ITypeBinding controllerClass = methodBinding.getDeclaringClass();
                        if (controllerClass == null) {
                            continue;
                        }

                        String controllerClassFqn = controllerClass.getQualifiedName();
                        ClassObject controllerClassObject = systemObject.getClassObject(controllerClassFqn);

                        String methodName = node.getName().getIdentifier() + "." + method.getName().getIdentifier();
                        CallGraphNode rootNode = new CallGraphNode(methodName);
                        rootNode.classObject = controllerClassObject;
                        rootNode.isTransactional = isTransactional(node, method, null);
                        
                        CallGraph callGraph = new CallGraph();
                        callGraph.addNode(rootNode);

                        findMethodCalls(rootNode, method, new HashSet<String>());
                        callGraphs.add(callGraph);
                    }

                    return super.visit(node);
                }
            });
        }

        return callGraphs;
    }

    private void findMethodCalls(final CallGraphNode parentNode, final MethodDeclaration method, final Set<String> visitedMethods) {
        method.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
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

        String fullMethodName = invokedMethodTypeFqn + "." + methodInvocation.getName().getIdentifier();
        if (!visitedMethods.add(fullMethodName)) {
            return;
        }

        if (persistedEntityType != null) {
            handlePersistenceCall(parentNode, invokedMethodType, fullMethodName, persistedEntityType);
        } else {
        	handleUserWrittenCall(parentNode, methodBinding, invokedMethodType, invokedMethodTypeFqn, fullMethodName, visitedMethods);
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
        boolean isPersistenceCall = invokedMethodName.equals("persist") || invokedMethodName.equals("merge");
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

    private void handlePersistenceCall(CallGraphNode parentNode, ITypeBinding invokedMethodType, String fullMethodName, ITypeBinding persistedEntityType) {
        CallGraphNode calledNode = new CallGraphNode(fullMethodName);

        ClassObject entityCreatedClass = systemObject.getClassObject(persistedEntityType.getQualifiedName());
        if (entityCreatedClass == null) {
            parentNode.calledMethods.add(calledNode);
            return;
        }

        calledNode.createdEntities.add(entityCreatedClass.getName());
        calledNode.createdEntitiesObjects.add(entityCreatedClass);

        parentNode.calledMethods.add(calledNode);
        parentNode.createdEntities.addAll(calledNode.createdEntities);
        parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
    }

    private void handleUserWrittenCall(CallGraphNode parentNode, IMethodBinding methodBinding, ITypeBinding invokedMethodType, String invokedMethodTypeName, String fullMethodName, Set<String> visitedMethods) {
        CallGraphNode calledNode = new CallGraphNode(fullMethodName);
        ClassObject invokedMethodClass = systemObject.getClassObject(invokedMethodTypeName);
        calledNode.classObject = invokedMethodClass;

        if (isEntityType(invokedMethodType)) {
            recordEntityAccess(calledNode, invokedMethodTypeName, invokedMethodClass);
        }

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

    private void recordEntityAccess(CallGraphNode calledNode, String invokedMethodTypeName, ClassObject invokedMethodClass) {
        calledNode.isEntityMethod = true;
        calledNode.accessedEntities.add(invokedMethodTypeName);
        if (invokedMethodClass != null) {
            calledNode.accessedEntitiesObjects.add(invokedMethodClass);
        }
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
        parentNode.accessedEntities.addAll(calledNode.accessedEntities);
        parentNode.accessedEntitiesObjects.addAll(calledNode.accessedEntitiesObjects);
        parentNode.definedEntities.addAll(calledNode.definedEntities);
        parentNode.definedEntitiesObjects.addAll(calledNode.definedEntitiesObjects);
        parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
        parentNode.createdEntities.addAll(calledNode.createdEntities);
        parentNode.creationRecords.addAll(calledNode.creationRecords);
        if (calledNode.isTransactional) {
            parentNode.isTransactional = true;
        }
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
        IVariableBinding fieldBinding = extractFieldBinding(writtenExpression);
        if (fieldBinding == null || !fieldBinding.isField()) {
            return;
        }

        ITypeBinding fieldType = fieldBinding.getDeclaringClass();
        if (fieldType == null || !isEntityType(fieldType)) {
            return;
        }

        String mutatedEntityFqn = fieldType.getQualifiedName();
        ClassObject mutatedEntityClass = systemObject.getClassObject(mutatedEntityFqn);
        if (mutatedEntityClass == null) {
            return;
        }

        parentNode.definedEntities.add(mutatedEntityFqn);
        parentNode.definedEntitiesObjects.add(mutatedEntityClass);
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
