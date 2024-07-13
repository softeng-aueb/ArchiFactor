package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class EndpointExtractor {

    public Map<String, List<String>> extractEndpoints(ICompilationUnit[] compilationUnits) {
        final Map<String, List<String>> callGraph = new HashMap<String, List<String>>();

        for (ICompilationUnit unit : compilationUnits) {
            CompilationUnit parse = parse(unit);
            parse.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration methodDeclaration) {
                    List<String> endpoints = new ArrayList<String>();
                    for (Object modifier : methodDeclaration.modifiers()) {
                        if (modifier instanceof Annotation) {
                            String annotation = ((Annotation) modifier).getTypeName().getFullyQualifiedName();
                            if (annotation.contains("Mapping")) {
                                endpoints.add(annotation);
                            }
                        }
                    }
                    callGraph.put(methodDeclaration.getName().toString(), endpoints);
                    return super.visit(methodDeclaration);
                }
            });
        }

        return callGraph;
    }

    private CompilationUnit parse(ICompilationUnit unit) {
        @SuppressWarnings("deprecation")
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }
}
