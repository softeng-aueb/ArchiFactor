package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import gr.uom.java.ast.SystemObject;

public class SpringBootCallGraphBuilder extends BaseCallGraphBuilder {

    private static final Set<String> CONTROLLER_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController",
        "Controller", 
        "RestController"
    ));

    private static final Set<String> ENDPOINT_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.RequestMapping",
        "GetMapping", 
        "PostMapping", 
        "PutMapping", 
        "PatchMapping", 
        "DeleteMapping", 
        "RequestMapping"
    ));

    private static final Set<String> TRANSACTIONAL_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "org.springframework.transaction.annotation.Transactional",
        "jakarta.transaction.Transactional",
        "javax.transaction.Transactional",
        "Transactional"
    ));

    private static final String CRUD_REPOSITORY = "org.springframework.data.repository.CrudRepository";
    private static final String LIST_CRUD_REPOSITORY = "org.springframework.data.repository.ListCrudRepository";
    private static final String JPA_REPOSITORY = "org.springframework.data.jpa.repository.JpaRepository";

    private static final Set<String> SAVE_METHODS = new HashSet<String>(Arrays.asList(
        "save", 
        "saveAll", 
        "saveAndFlush", 
        "saveAllAndFlush"
    ));

    public SpringBootCallGraphBuilder(IJavaProject javaProject, SystemObject systemObject) throws JavaModelException {
        super(javaProject, systemObject);
    }

    @Override
    protected boolean isController(TypeDeclaration node) {
        return hasAnyAnnotation(node.modifiers(), CONTROLLER_ANNOTATIONS);
    }

    @Override
    protected boolean isEndpoint(MethodDeclaration method) {
        return hasAnyAnnotation(method.modifiers(), ENDPOINT_ANNOTATIONS);
    }

    @Override
    protected boolean isTransactional(MethodDeclaration method) {
        return hasAnyAnnotation(method.modifiers(), TRANSACTIONAL_ANNOTATIONS);
    }

    @Override
    protected boolean isTransactional(TypeDeclaration type) {
        return hasAnyAnnotation(type.modifiers(), TRANSACTIONAL_ANNOTATIONS);
    }

    @Override
    protected ITypeBinding resolveFrameworkPersistedEntityType(MethodInvocation invocation, IMethodBinding binding) {
        if (!SAVE_METHODS.contains(binding.getName())) {
            return null;
        }

        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (isSubtypeOf(declaringClass, JPA_REPOSITORY)) {
            return extractEntityTypeFromRepositoryHierarchy(invocation, JPA_REPOSITORY, 0);
        }
        if (isSubtypeOf(declaringClass, LIST_CRUD_REPOSITORY)) {
            return extractEntityTypeFromRepositoryHierarchy(invocation, LIST_CRUD_REPOSITORY, 0);
        }
        if (isSubtypeOf(declaringClass, CRUD_REPOSITORY)) {
            return extractEntityTypeFromRepositoryHierarchy(invocation, CRUD_REPOSITORY, 0);
        }
        return null;
    }

    @Override
    protected IType selectPrimaryImplementation(List<IType> candidates) {
        for (IType candidate : candidates) {
            if (hasPrimaryAnnotation(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasPrimaryAnnotation(IType type) {
        try {
            for (IAnnotation annotation : type.getAnnotations()) {
                String name = annotation.getElementName();
                if (name.equals("Primary") || name.equals("org.springframework.context.annotation.Primary")) {
                    return true;
                }
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return false;
    }
}
