package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
//import gr.uom.java.ast.association.Association;
//import gr.uom.java.ast.association.AssociationDetection;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;
// import gr.uom.java.ast.decomposition.cfg.PlainVariable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class CallGraphBuilder {

    private final IJavaProject javaProject;
    private final SystemObject systemObject;
    private final String projectPackagePrefix;

    public CallGraphBuilder(IJavaProject javaProject, SystemObject systemObject) throws JavaModelException {
        this.javaProject = javaProject;
        this.systemObject = systemObject;
        this.projectPackagePrefix = determineProjectPackagePrefix(javaProject.getPackageFragments());
    }

    public List<CallGraph> buildCallGraphs() throws JavaModelException {
        final List<CallGraph> callGraphs = new ArrayList<CallGraph>();
        List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();

        IPackageFragment[] packageFragments = javaProject.getPackageFragments();

        for (IPackageFragment packageFragment : packageFragments) {
            if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                for (ICompilationUnit unit : packageFragment.getCompilationUnits()) {
                    compilationUnits.add(unit);
                }
            }
        }

        for (ICompilationUnit unit : compilationUnits) {
            @SuppressWarnings("deprecation")
			ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(unit);
            parser.setResolveBindings(true);
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    if (isController(node)) {
                        for (MethodDeclaration method : node.getMethods()) {
                            if (hasMappingAnnotation(method)) {
                                CallGraph callGraph = new CallGraph();
                                String methodName = node.getName().getIdentifier() + "." + method.getName().getIdentifier();
                                CallGraphNode rootNode = new CallGraphNode(methodName);
                                IMethodBinding methodBinding = method.resolveBinding();
                                ITypeBinding declaringClass = methodBinding.getDeclaringClass();
                                String fullClassName = declaringClass.getQualifiedName();
                            	ClassObject classObjecOfEntity = systemObject.getClassObject(fullClassName);
                                rootNode.setClassObject(classObjecOfEntity);
                                if(isTransactional(method)) {
                                	rootNode.transactional = true;
                                }
                                callGraph.addNode(rootNode);
                                findMethodCalls(rootNode, method, new HashSet<String>());
                                callGraphs.add(callGraph);
                            }
                        }
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
            public boolean visit(final MethodInvocation methodInvocation) {
                IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
                if (methodBinding != null) {
                    ITypeBinding declaringClass = methodBinding.getDeclaringClass();
                    
                    String className = declaringClass.getQualifiedName();
                    String methodName = methodInvocation.getName().toString();
                    String persistClass ="io.quarkus.hibernate.orm.panache.PanacheRepositoryBase";
                    Boolean userWritten = className.startsWith(projectPackagePrefix);
                    Boolean persistMethod = className.startsWith(persistClass) && methodName.equals("persist");
                    System.out.println("Class: " + className + " --> " + methodName + ", Starts with " + projectPackagePrefix.toString() + " ? " + userWritten);
                    
                    
                    if (declaringClass != null && (userWritten  || persistMethod)) {
                    	String fullClassName = declaringClass.getQualifiedName();
                    	ClassObject classObjecOfEntity = systemObject.getClassObject(fullClassName);
                        String fulldMethodName = fullClassName + "." + methodInvocation.getName().getIdentifier();
                        if (visitedMethods.add(fulldMethodName)) {
                            final CallGraphNode calledNode = new CallGraphNode(fulldMethodName);
                            calledNode.setClassObject(classObjecOfEntity);
                            // Check if persist method
                            if (persistMethod) {
                            	ClassObject classCreated = systemObject.getClassObject(ExtractClassNameOfPersist(fullClassName));
                            	calledNode.allEntities.add(classCreated);
                            	calledNode.allEntitiesNames.add(declaringClass.getQualifiedName());
	                            parentNode.addCalledMethod(calledNode);
                            	calledNode.createdEntities.add(classCreated.getName());
                            	calledNode.createdEntitiesObjects.add(classCreated);
                                parentNode.createdEntities.addAll(calledNode.createdEntities);
                                parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
                            }
                            // Check if user method
                            else if (userWritten) {
                            	boolean isEntity = isEntityMethod(declaringClass, javaProject);
                                calledNode.setEntityMethod(isEntity);
                                if(isEntity) {
                                	calledNode.accessedEntities.add(declaringClass.getQualifiedName());
                                	calledNode.allEntities.add(classObjecOfEntity);
                                	calledNode.allEntitiesNames.add(declaringClass.getQualifiedName());
                                }
                                final IMethod method = (IMethod) methodBinding.getJavaElement();
                                if (method != null) {
    	                            ClassObject classObjectOfMethod = systemObject.getClassObject(fullClassName);
    	                            MethodObject methodObject = (MethodObject) systemObject.getMethodObject(method);
    	                            calledNode.setClassObject(classObjectOfMethod);
    	                            calledNode.setMethodObject(methodObject);
    	                            calledNode.setDefinedFields(getDefinedAttributes(methodObject));
    	                            calledNode.isReadOnly = true;
    	                            if(calledNode.definedFields != null && calledNode.definedFields.size() != 0 &&  calledNode.isEntityMethod()) {
    	                            	calledNode.isReadOnly = false;
    	                            	calledNode.definedEntities.add(declaringClass.getQualifiedName());
    	                            	ClassObject definedClassEntity = systemObject.getClassObject(declaringClass.getQualifiedName());
    	                            	calledNode.definedEntitiesObjects.add(definedClassEntity);
    	                            	calledNode.allEntities.add(calledNode.classObject);
    	                            	calledNode.allEntitiesNames.add(declaringClass.getQualifiedName());
    	                            	parentNode.isReadOnly = false;
    	                            }
    	                            parentNode.addCalledMethod(calledNode);
                                    ICompilationUnit cu = method.getCompilationUnit();
                                    if (cu != null) {
                                        @SuppressWarnings("deprecation")
    									ASTParser parser = ASTParser.newParser(AST.JLS8);
                                        parser.setKind(ASTParser.K_COMPILATION_UNIT);
                                        parser.setSource(cu);
                                        parser.setResolveBindings(true);
                                        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
                                        unit.accept(new ASTVisitor() {
                                            @Override
                                            public boolean visit(TypeDeclaration type) {
                                                if (type.getName().getIdentifier().equals(method.getDeclaringType().getElementName())) {
                                                    for (MethodDeclaration md : type.getMethods()) {
                                                        if (md.getName().getIdentifier().equals(methodInvocation.getName().getIdentifier())) {
                                                            findMethodCalls(calledNode, md, visitedMethods);
                                                            parentNode.accessedEntities.addAll(calledNode.accessedEntities);
                                                            parentNode.definedEntities.addAll(calledNode.definedEntities);
                                                            parentNode.definedEntitiesObjects.addAll(calledNode.definedEntitiesObjects);
                                                            parentNode.createdEntitiesObjects.addAll(calledNode.createdEntitiesObjects);
                                                            parentNode.createdEntities.addAll(calledNode.createdEntities);
                                                            parentNode.allEntities.addAll(calledNode.allEntities);
                                                            parentNode.allEntitiesNames.addAll(calledNode.allEntitiesNames);
                                                            parentNode.creationRecords.addAll(calledNode.creationRecords);
                                                            if(calledNode.transactional) {
                                                            	parentNode.transactional = true;
                                                            }
                                                        }
                                                    }
                                                }
                                                return super.visit(type);
                                            }
                                        });
                                    }
                                }
                            }
                            
                        }
                            
                    }
                }
                return super.visit(methodInvocation);
            }
            @Override
            public boolean visit(ClassInstanceCreation node) {
                IMethodBinding constructorBinding = node.resolveConstructorBinding();
                if (constructorBinding != null) {
                    ITypeBinding declaringClass = constructorBinding.getDeclaringClass();
                    IAnnotationBinding[] annotations = declaringClass.getAnnotations();
                    System.out.println("Constructor called: " + declaringClass.getName() + " parent: " + parentNode.getMethodName());
                    for(IAnnotationBinding annotation: annotations) {
                    	String annotationName = annotation.getName();
                    	if (annotationName.equals("Entity")) {
                        	ClassObject classCreated = systemObject.getClassObject(declaringClass.getQualifiedName());
                        	parentNode.createdEntitiesObjects.add(classCreated);
                    		parentNode.createdEntities.add(declaringClass.getQualifiedName());
                    		// ClassObject parentClass =systemObject.getClassObject(ExtractClassNameOfPersist(parentNode.getQualifiedName())); 
                    		CreationRecord creation = new CreationRecord(classCreated, parentNode.classObject);
                    		parentNode.addCreationRecord(creation);
                        }
                    }
                }
                return super.visit(node);
            }
        });
    }

    private boolean isController(TypeDeclaration node) {
        for (Object modifier : node.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation annotation = (Annotation) modifier;
                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                if (annotationName.equals("RestController") || annotationName.equals("Controller") || annotationName.equals("Path")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isTransactional(MethodDeclaration method) {
        for (Object modifier : method.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation annotation = (Annotation) modifier;
                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                if (annotationName.equals("Transactional")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private List<AbstractVariable> getDefinedAttributes(MethodObject method) {
    	ArrayList<AbstractVariable> combinedList = new ArrayList<AbstractVariable>();
        combinedList.addAll(method.getNonDistinctDefinedFieldsThroughFields());
        // combinedList.addAll(method.getNonDistinctDefinedFieldsThroughLocalVariables());
        combinedList.addAll(method.getNonDistinctDefinedFieldsThroughParameters());
        combinedList.addAll(method.getNonDistinctDefinedFieldsThroughThisReference());
        return combinedList;
    }

    private boolean isEntityMethod(ITypeBinding declaringClass, IJavaProject javaProject) {
        try {
            IType type = javaProject.findType(declaringClass.getQualifiedName());
            if (type != null) {
                ICompilationUnit cu = type.getCompilationUnit();
                @SuppressWarnings("deprecation")
				ASTParser parser = ASTParser.newParser(AST.JLS8);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setSource(cu);
                parser.setResolveBindings(true);
                CompilationUnit unit = (CompilationUnit) parser.createAST(null);
                final boolean[] isEntity = {false};
                unit.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(TypeDeclaration node) {
                        for (Object modifier : node.modifiers()) {
                            if (modifier instanceof Annotation) {
                                Annotation annotation = (Annotation) modifier;
                                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                                if (annotationName.equals("Entity")) {
                                    isEntity[0] = true;
                                    return false;
                                }
                            }
                        }
                        return super.visit(node);
                    }
                });
                return isEntity[0];
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private String ExtractClassNameOfPersist(String input) {

        // Regular expression to match the first generic parameter
        String regex = "<(.*?),";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String className = matcher.group(1); // Extracts the first generic parameter
            return className;
        } else {
            System.out.println("No match found");
            return "";
        }
    }
    
    private boolean hasMappingAnnotation(MethodDeclaration method) {
        for (Object modifier : method.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation annotation = (Annotation) modifier;
                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                if (annotationName.equals("GetMapping") || annotationName.equals("PostMapping") ||
                        annotationName.equals("PutMapping") || annotationName.equals("DeleteMapping") ||
                        annotationName.equals("RequestMapping") || annotationName.equals("Path")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String determineProjectPackagePrefix(IPackageFragment[] packageFragments) throws JavaModelException {
        List<String> packageNames = new ArrayList<String>();
        for (IPackageFragment packageFragment : packageFragments) {
            if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                packageNames.add(packageFragment.getElementName());
            }
        }
        return commonPrefix(packageNames);
    }

    private String commonPrefix(List<String> strings) {
        if (strings.isEmpty()) return "";
        strings.removeAll(Arrays.asList("", null));
        String prefix = strings.get(0);
        for (String s : strings) {
            while (!s.startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return "";
                }
            }
        }
        return prefix;
    }

	public SystemObject getSystemObject() {
		return systemObject;
	}
}
