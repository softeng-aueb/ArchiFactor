package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallGraphBuilder {

    private String projectPackagePrefix;

    public Map<String, List<String>> buildCallGraph() throws JavaModelException {
        final Map<String, List<String>> callGraph = new HashMap<String, List<String>>();
        List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();

        // Get all Java projects in the workspace
        IJavaProject[] javaProjects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();

        // Iterate through each project
        for (IJavaProject javaProject : javaProjects) {
            System.out.println("Project: " + javaProject.getElementName());

            // Get all package fragments in the project
            IPackageFragment[] packageFragments = javaProject.getPackageFragments();

            // Determine the project package prefix dynamically
            projectPackagePrefix = determineProjectPackagePrefix(packageFragments);
            System.out.println("Project Package Prefix: " + projectPackagePrefix); // Debugging log

            // Iterate through each package fragment
            for (IPackageFragment packageFragment : packageFragments) {
                // Get all compilation units (Java source files) in the package fragment
                if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    for (ICompilationUnit unit : packageFragment.getCompilationUnits()) {
                        System.out.println("Compilation Unit: " + unit.getElementName());
                        compilationUnits.add(unit);
                    }
                }
            }
        }

        // Process each compilation unit
        for (ICompilationUnit unit : compilationUnits) {
            // Parse the compilation unit
            @SuppressWarnings("deprecation")
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(unit);
            parser.setResolveBindings(true); // Enable bindings resolution
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            // Visit types and methods
            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    // Check if the class is annotated with @RestController or @Controller
                    if (isController(node)) {
                        for (MethodDeclaration method : node.getMethods()) {
                            // Check for mapping annotations on the method
                            if (hasMappingAnnotation(method)) {
                                String methodName = node.getName().getIdentifier() + "." + method.getName().getIdentifier();
                                List<String> callList = new ArrayList<String>();
                                findMethodCalls(node.getName().getIdentifier(), method, callList, new HashSet<Object>());
                                callGraph.put(methodName, callList);
                            }
                        }
                    }
                    return super.visit(node);
                }
            });
        }

        return callGraph;
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

    private void findMethodCalls(final String parentMethod, MethodDeclaration method, final List<String> callList, final HashSet<Object> hashSet) {
        method.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation methodInvocation) {
                final String calledMethod = methodInvocation.getName().getIdentifier();
                IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
                if (methodBinding != null) {
                    ITypeBinding declaringClass = methodBinding.getDeclaringClass();
                    if (declaringClass != null) {
                        String fullyQualifiedClassName = declaringClass.getQualifiedName();
                        if (fullyQualifiedClassName != null && fullyQualifiedClassName.startsWith(projectPackagePrefix)) {
                            final String fullyQualifiedMethodName = fullyQualifiedClassName + "." + calledMethod;
                            System.out.println("Resolved call: " + parentMethod + " -> " + fullyQualifiedMethodName); // Debugging log
                            if (!hashSet.contains(fullyQualifiedMethodName)) {
                                hashSet.add(fullyQualifiedMethodName);
                                callList.add(fullyQualifiedMethodName);

                                final IMethod method = (IMethod) methodBinding.getJavaElement();
                                if (method != null) {
                                    ICompilationUnit cu = method.getCompilationUnit();
                                    if (cu != null) {
                                        @SuppressWarnings("deprecation")
                                        ASTParser parser = ASTParser.newParser(AST.JLS8);
                                        parser.setKind(ASTParser.K_COMPILATION_UNIT);
                                        parser.setSource(cu);
                                        parser.setResolveBindings(true); // Enable bindings resolution
                                        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
                                        unit.accept(new ASTVisitor() {
                                            @Override
                                            public boolean visit(TypeDeclaration type) {
                                                if (type.getName().getIdentifier().equals(method.getDeclaringType().getElementName())) {
                                                    for (MethodDeclaration md : type.getMethods()) {
                                                        if (md.getName().getIdentifier().equals(calledMethod)) {
                                                            findMethodCalls(fullyQualifiedMethodName, md, callList, hashSet);
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
                } else {
                    System.out.println("Could not resolve binding for: " + parentMethod + " -> " + calledMethod); // Debugging log
                }
                return super.visit(methodInvocation);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private boolean isController(TypeDeclaration node) {
        for (IExtendedModifier modifier : (List<IExtendedModifier>) node.modifiers()) {
            if (modifier.isAnnotation()) {
                Annotation annotation = (Annotation) modifier;
                if (annotation.getTypeName().getFullyQualifiedName().equals("RestController") ||
                    annotation.getTypeName().getFullyQualifiedName().equals("Controller")) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasMappingAnnotation(MethodDeclaration method) {
        for (IExtendedModifier modifier : (List<IExtendedModifier>) method.modifiers()) {
            if (modifier.isAnnotation()) {
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
}
