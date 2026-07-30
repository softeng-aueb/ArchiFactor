package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import gr.uom.java.ast.SystemObject;

public class QuarkusCallGraphBuilder extends BaseCallGraphBuilder {

    private static final Set<String> CONTROLLER_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "jakarta.ws.rs.Path", 
        "javax.ws.rs.Path", 
        "Path"
    ));

    private static final Set<String> ENDPOINT_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "jakarta.ws.rs.GET", 
        "jakarta.ws.rs.POST", 
        "jakarta.ws.rs.PUT", 
        "jakarta.ws.rs.PATCH", 
        "jakarta.ws.rs.DELETE", 
        "jakarta.ws.rs.Path",
        "javax.ws.rs.GET", 
        "javax.ws.rs.POST", 
        "javax.ws.rs.PUT", 
        "javax.ws.rs.PATCH", 
        "javax.ws.rs.DELETE", 
        "javax.ws.rs.Path",
        "GET", 
        "POST", 
        "PUT", 
        "PATCH", 
        "DELETE", 
        "Path"
    ));

    private static final Set<String> SCHEDULED_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "io.quarkus.scheduler.Scheduled",
        "Scheduled"
    ));

    private static final Set<String> TRANSACTIONAL_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "jakarta.transaction.Transactional", 
        "javax.transaction.Transactional", 
        "Transactional"
    ));

    private static final String PANACHE_REPOSITORY_BASE = "io.quarkus.hibernate.orm.panache.PanacheRepositoryBase";
    private static final String PANACHE_REPOSITORY = "io.quarkus.hibernate.orm.panache.PanacheRepository";
    private static final String PANACHE_ENTITY_BASE = "io.quarkus.hibernate.orm.panache.PanacheEntityBase";

    private static final Set<String> WRITE_METHODS = new HashSet<String>(Arrays.asList(
        "persist",
        "merge",
        "delete",
        "deleteById",
        "deleteAll"
    ));

    public QuarkusCallGraphBuilder(IJavaProject javaProject, SystemObject systemObject) throws JavaModelException {
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
    protected boolean isScheduledJobMethod(MethodDeclaration method) {
        return hasAnyAnnotation(method.modifiers(), SCHEDULED_ANNOTATIONS);
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
        if (!WRITE_METHODS.contains(binding.getName())) {
            return null;
        }

        ITypeBinding declaringClass = binding.getDeclaringClass();
        String fqn = declaringClass.getErasure().getQualifiedName();

        if (fqn.equals(PANACHE_REPOSITORY_BASE) || isSubtypeOf(declaringClass, PANACHE_REPOSITORY_BASE)) {
            return extractEntityTypeFromRepositoryHierarchy(invocation, PANACHE_REPOSITORY_BASE, 0);
        }
        if (fqn.equals(PANACHE_REPOSITORY) || isSubtypeOf(declaringClass, PANACHE_REPOSITORY)) {
            return extractEntityTypeFromRepositoryHierarchy(invocation, PANACHE_REPOSITORY, 0);
        }
        if (fqn.equals(PANACHE_ENTITY_BASE) || isSubtypeOf(declaringClass, PANACHE_ENTITY_BASE)) {
            if (invocation.getExpression() != null) {
                return invocation.getExpression().resolveTypeBinding();
            }
            return declaringClass;
        }
        return null;
    }

    @Override
    protected IType selectPrimaryImplementation(List<IType> candidates) {
        IType bestAlternative = null;
        int bestPriority = Integer.MIN_VALUE;
        for (IType candidate : candidates) {
            if (!hasAlternativeAnnotation(candidate)) {
                continue;
            }
            int priority = readPriority(candidate);
            if (bestAlternative == null || priority > bestPriority) {
                bestAlternative = candidate;
                bestPriority = priority;
            }
        }
        return bestAlternative;
    }

    private boolean hasAlternativeAnnotation(IType type) {
        try {
            for (IAnnotation annotation : type.getAnnotations()) {
                String name = annotation.getElementName();
                if (name.equals("Alternative") || name.equals("jakarta.enterprise.inject.Alternative") || name.equals("javax.enterprise.inject.Alternative")) {
                    return true;
                }
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return false;
    }

    private int readPriority(IType type) {
        try {
            for (IAnnotation annotation : type.getAnnotations()) {
                String name = annotation.getElementName();
                if (!name.equals("Priority") && !name.equals("jakarta.annotation.Priority") && !name.equals("javax.annotation.Priority")) {
                    continue;
                }
                for (IMemberValuePair pair : annotation.getMemberValuePairs()) {
                    if (pair.getValue() instanceof Integer) {
                        return (Integer) pair.getValue();
                    }
                }
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
