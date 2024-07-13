package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallGraphBuilder {

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

            // Iterate through each package fragment
            for (IPackageFragment packageFragment : packageFragments) {
                System.out.println("Package: " + packageFragment.getElementName());

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
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(unit);
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
                                String methodName = method.getName().getIdentifier();
                                List<String> endpoints = callGraph.getOrDefault(node.getName().getIdentifier(), new ArrayList<String>());
                                endpoints.add(methodName);
                                callGraph.put(node.getName().getIdentifier(), endpoints);
                            }
                        }
                    }
                    return super.visit(node);
                }
            });
        }

        return callGraph;
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
