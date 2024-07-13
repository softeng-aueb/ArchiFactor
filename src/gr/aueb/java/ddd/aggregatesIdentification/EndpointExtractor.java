package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndpointExtractor {

    @SuppressWarnings("deprecation")
	public Map<String, List<String>> extractEndpoints(ICompilationUnit[] compilationUnits) {
        Map<String, List<String>> endpoints = new HashMap<>();

        for (ICompilationUnit cu : compilationUnits) {
            ASTParser parser = ASTParser.newParser(AST.JLS16);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(cu);
            parser.setResolveBindings(true);

            CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
            compilationUnit.accept(new ASTVisitor() {
                @Override
                public boolean visit(TypeDeclaration node) {
                    for (MethodDeclaration method : node.getMethods()) {
                        if (isRestController(method)) {
                            List<String> endpointList = extractEndpointsFromMethod(method);
                            if (endpointList != null) {
                                endpoints.put(method.getName().toString(), endpointList);
                            }
                        }
                    }
                    return super.visit(node);
                }
            });
        }

        return endpoints;
    }

    @SuppressWarnings("unchecked")
	private boolean isRestController(MethodDeclaration method) {
        for (IExtendedModifier modifier : (List<IExtendedModifier>) method.modifiers()) {
            if (modifier.isAnnotation()) {
                Annotation annotation = (Annotation) modifier;
                if (annotation.getTypeName().getFullyQualifiedName().startsWith("RequestMapping")
                        || annotation.getTypeName().getFullyQualifiedName().startsWith("GetMapping")
                        || annotation.getTypeName().getFullyQualifiedName().startsWith("PostMapping")
                        || annotation.getTypeName().getFullyQualifiedName().startsWith("PutMapping")
                        || annotation.getTypeName().getFullyQualifiedName().startsWith("DeleteMapping")) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
	private List<String> extractEndpointsFromMethod(MethodDeclaration method) {
        List<String> endpoints = new ArrayList<>();
        for (IExtendedModifier modifier : (List<IExtendedModifier>) method.modifiers()) {
            if (modifier.isAnnotation()) {
                Annotation annotation = (Annotation) modifier;
                String annotationName = annotation.getTypeName().getFullyQualifiedName();
                if (annotationName.startsWith("RequestMapping")
                        || annotationName.startsWith("GetMapping")
                        || annotationName.startsWith("PostMapping")
                        || annotationName.startsWith("PutMapping")
                        || annotationName.startsWith("DeleteMapping")) {
                    endpoints.add(annotationName + extractAnnotationValue(annotation));
                }
            }
        }
        return endpoints;
    }

    @SuppressWarnings("unchecked")
	private String extractAnnotationValue(Annotation annotation) {
        if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            for (MemberValuePair pair : (List<MemberValuePair>) normalAnnotation.values()) {
                if (pair.getName().toString().equals("value")) {
                    return pair.getValue().toString();
                }
            }
        }
        return "";
    }
}
