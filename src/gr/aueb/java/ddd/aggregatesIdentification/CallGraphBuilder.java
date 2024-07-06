package gr.aueb.java.ddd.aggregatesIdentification;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CallGraphBuilder {

    public Map<String, List<String>> buildCallGraph() throws JavaModelException {
        Map<String, List<String>> callGraph = new HashMap<String, List<String>>();
        SystemObject systemObject = SystemObjectProvider.getSystemObject();

        if (systemObject == null) {
            return callGraph;
        }

        for (ClassObject classObject : systemObject.getClassObjects()) {
            if (isRestController(classObject)) {
                for (MethodObject method : classObject.getMethodList()) {
                    if (hasRequestMapping(method)) {
                        List<String> methodCalls = findMethodCalls(method);
                        callGraph.put(formatEndpoint(classObject, method), methodCalls);
                    }
                }
            }
        }
        return callGraph;
    }

    private boolean isRestController(ClassObject classObject) {
        return classObject.getAnnotations().stream()
                .anyMatch(new Predicate<Annotation>() {
					public boolean test(Annotation annotation) {
						return annotation.getTypeName().getFullyQualifiedName().endsWith("RestController");
					}
				});
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
	private boolean hasRequestMapping(MethodObject methodObject) {
        MethodDeclaration methodDeclaration = methodObject.getMethodDeclaration();
        return methodDeclaration.modifiers().stream()
                .anyMatch(new Predicate() {
					public boolean test(Object modifier) {
						return modifier instanceof NormalAnnotation &&
						        ((NormalAnnotation) modifier).getTypeName().getFullyQualifiedName().matches("GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping") ||
						        modifier instanceof SingleMemberAnnotation &&
						        ((SingleMemberAnnotation) modifier).getTypeName().getFullyQualifiedName().matches("GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping") ||
						        modifier instanceof MarkerAnnotation &&
						        ((MarkerAnnotation) modifier).getTypeName().getFullyQualifiedName().matches("GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping");
					}
				});
    }

    private String formatEndpoint(ClassObject classObject, MethodObject methodObject) {
        String className = classObject.getName();
        String methodName = methodObject.getName();
        return className + "#" + methodName;
    }

    private List<String> findMethodCalls(MethodObject methodObject) {
        List<String> methodCalls = new ArrayList<String>();
        MethodCallVisitor visitor = new MethodCallVisitor();
        methodObject.getMethodDeclaration().accept(visitor);

        for (MethodInvocation methodInvocation : visitor.getMethodInvocations()) {
            methodCalls.add(methodInvocation.getName().getFullyQualifiedName());
        }

        return methodCalls;
    }

    private static class MethodCallVisitor extends ASTVisitor {
        private final List<MethodInvocation> methodInvocations = new ArrayList<MethodInvocation>();

        @Override
        public boolean visit(MethodInvocation node) {
            methodInvocations.add(node);
            return super.visit(node);
        }

        public List<MethodInvocation> getMethodInvocations() {
            return methodInvocations;
        }
    }
}
