package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallGraphBuilder {

    private String projectPackagePrefix;

    public List<CallGraph> buildCallGraphs() throws JavaModelException {
        final List<CallGraph> callGraphs = new ArrayList<CallGraph>();
        List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();

        IJavaProject[] javaProjects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();

        for (IJavaProject javaProject : javaProjects) {
            IPackageFragment[] packageFragments = javaProject.getPackageFragments();
            projectPackagePrefix = determineProjectPackagePrefix(packageFragments);

            for (IPackageFragment packageFragment : packageFragments) {
                if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    for (ICompilationUnit unit : packageFragment.getCompilationUnits()) {
                        compilationUnits.add(unit);
                    }
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
                                findMethodCalls(rootNode, method, new HashSet<Object>());
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

    private void findMethodCalls(final CallGraphNode parentNode, MethodDeclaration method, final HashSet<Object> hashSet) {
        method.accept(new ASTVisitor() {
            @Override
            public boolean visit(final MethodInvocation methodInvocation) {
                IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
                if (methodBinding != null) {
                    ITypeBinding declaringClass = methodBinding.getDeclaringClass();
                    if (declaringClass != null) {
                        String fullyQualifiedMethodName = declaringClass.getQualifiedName() + "." + methodInvocation.getName().getIdentifier();
                        if (fullyQualifiedMethodName.startsWith(projectPackagePrefix) && hashSet.add(fullyQualifiedMethodName)) {
                            final CallGraphNode calledNode = new CallGraphNode(fullyQualifiedMethodName);
                            parentNode.addCalledMethod(calledNode);
                            final IMethod method = (IMethod) methodBinding.getJavaElement();
                            if (method != null) {
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
                                                        findMethodCalls(calledNode, md, hashSet);
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
                if (annotationName.equals("RestController") || annotationName.equals("Controller")) {
                    return true;
                }
            }
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
                        annotationName.equals("RequestMapping")) {
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
}
