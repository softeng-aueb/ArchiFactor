package gr.aueb.java.archifactor.modules.identification;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.uom.java.ast.SystemObject;

public class CallGraphBuilderFactory {

    public static BaseCallGraphBuilder create(FrameworkType frameworkType, IJavaProject javaProject, SystemObject systemObject) throws JavaModelException {
        switch (frameworkType) {
            case QUARKUS:
                return new QuarkusCallGraphBuilder(javaProject, systemObject);
            case SPRING_BOOT:
                return new SpringBootCallGraphBuilder(javaProject, systemObject);
            default:
                throw new IllegalArgumentException("Unsupported framework type: " + frameworkType);
        }
    }
}
