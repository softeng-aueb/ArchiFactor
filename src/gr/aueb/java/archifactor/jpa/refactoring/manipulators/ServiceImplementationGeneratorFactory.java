package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.aueb.java.archifactor.jpa.enums.PersistenceNamespace;
import gr.uom.java.ast.SystemObject;

public class ServiceImplementationGeneratorFactory {

	public static BaseServiceImplementationGenerator createGenerator(FrameworkType frameworkType, IJavaProject project, SystemObject systemObject, PersistenceNamespace persistenceNamespace) {
		switch (frameworkType) {
			case QUARKUS:
				return new QuarkusServiceImplementationGenerator(project, systemObject, persistenceNamespace);
			case SPRING_BOOT:
				return new SpringBootServiceImplementationGenerator(project, systemObject, persistenceNamespace);
			default:
				throw new IllegalArgumentException("Unsupported framework type: " + frameworkType);
		}
	}
}
