package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;

import gr.uom.java.ast.SystemObject;
import gr.aueb.java.archifactor.refactoring.views.FrameworkType;

public class ServiceImplementationGeneratorFactory {

	public static BaseServiceImplementationGenerator createGenerator(FrameworkType frameworkType, IJavaProject project, SystemObject systemObject) {
		switch (frameworkType) {
			case QUARKUS:
				return new QuarkusServiceImplementationGenerator(project, systemObject);
			case SPRING_BOOT:
				return new SpringBootServiceImplementationGenerator(project, systemObject);
			default:
				throw new IllegalArgumentException("Unsupported framework type: " + frameworkType);
		}
	}
}
