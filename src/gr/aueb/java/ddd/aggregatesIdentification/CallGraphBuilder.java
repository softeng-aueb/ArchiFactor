package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;
// import gr.uom.java.ast.decomposition.cfg.PlainVariable;

import java.util.*;

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
                    if (declaringClass != null && declaringClass.getQualifiedName().startsWith(projectPackagePrefix)) {
                    	String fullClassName = declaringClass.getQualifiedName();
                        String fulldMethodName = fullClassName + "." + methodInvocation.getName().getIdentifier();
                        if (visitedMethods.add(fulldMethodName)) {
                            final CallGraphNode calledNode = new CallGraphNode(fulldMethodName);
                            calledNode.setEntityMethod(isEntityMethod(declaringClass, javaProject));
                            final IMethod method = (IMethod) methodBinding.getJavaElement();
                            if (method != null) {
	                            ClassObject classObjectOfMethod = systemObject.getClassObject(fullClassName);
	                            MethodObject methodObject = (MethodObject) systemObject.getMethodObject(method);
	                            calledNode.setClassObject(classObjectOfMethod);
	                            calledNode.setMethodObject(methodObject);
	                            calledNode.setDefinedFields(getDefinedAttributes(methodObject));
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
                return super.visit(methodInvocation);
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
