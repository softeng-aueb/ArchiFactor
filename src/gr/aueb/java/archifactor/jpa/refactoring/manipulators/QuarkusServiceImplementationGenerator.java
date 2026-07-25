package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;

import gr.aueb.java.archifactor.jpa.enums.PersistenceNamespace;
import gr.uom.java.ast.SystemObject;

import java.util.Arrays;
import java.util.List;

public class QuarkusServiceImplementationGenerator extends BaseServiceImplementationGenerator {

	public QuarkusServiceImplementationGenerator(IJavaProject project, SystemObject systemObject, PersistenceNamespace persistenceNamespace) {
		super(project, systemObject, persistenceNamespace);
	}

	@Override
	protected List<String> getFrameworkImports() {
		return Arrays.asList(
			"io.quarkus.arc.Unremovable",
			persistenceNamespace.type("inject.Inject"),
			persistenceNamespace.type("inject.Singleton")
		);
	}

	@Override
	protected List<String> getClassAnnotations() {
		return Arrays.asList("@Unremovable", "@Singleton");
	}

	@Override
	protected String getEntityManagerFieldAnnotation() {
		return "@Inject";
	}
}
