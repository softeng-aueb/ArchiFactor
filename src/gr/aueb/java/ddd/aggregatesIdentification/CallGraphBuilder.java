package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.JavaModelException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallGraphBuilder {

    public Map<String, List<String>> buildCallGraph() throws JavaModelException {
        Map<String, List<String>> callGraph = new HashMap<>();
        List<ICompilationUnit> compilationUnits = new ArrayList<>();

        // Get all Java projects in the workspace
        IJavaProject[] javaProjects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();

        // Iterate through each project
        for (IJavaProject javaProject : javaProjects) {
            // Get all package fragments in the project
            IPackageFragment[] packageFragments = javaProject.getPackageFragments();

            // Iterate through each package fragment
            for (IPackageFragment packageFragment : packageFragments) {
                // Get all compilation units (Java source files) in the package fragment
                if (packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    for (ICompilationUnit compilationUnit : packageFragment.getCompilationUnits()) {
                        compilationUnits.add(compilationUnit);
                    }
                }
            }
        }

        EndpointExtractor extractor = new EndpointExtractor();
        callGraph = extractor.extractEndpoints(compilationUnits.toArray(new ICompilationUnit[0]));

        // Print or use the endpoints as needed
        for (Map.Entry<String, List<String>> entry : callGraph.entrySet()) {
            System.out.println("Method: " + entry.getKey() + ", Endpoints: " + entry.getValue());
        }

        return callGraph;
    }
}

