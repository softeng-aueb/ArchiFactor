package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;

import gr.uom.java.ast.SystemObject;

import java.util.Arrays;
import java.util.List;

public class QuarkusServiceImplementationGenerator extends BaseServiceImplementationGenerator {

	public QuarkusServiceImplementationGenerator(IJavaProject project, SystemObject systemObject) {
		super(project, systemObject);
	}

	@Override
	protected List<String> getFrameworkImports() {
		return Arrays.asList(
			"jakarta.inject.Inject",
			"jakarta.inject.Singleton"
		);
	}

	@Override
	protected String getClassAnnotation() {
		return "@Singleton";
	}

	@Override
	protected String getEntityManagerFieldAnnotation() {
		return "@Inject";
	}
}
