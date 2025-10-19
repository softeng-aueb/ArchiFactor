package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;

import gr.uom.java.ast.SystemObject;

import java.util.Arrays;
import java.util.List;

public class SpringBootServiceImplementationGenerator extends BaseServiceImplementationGenerator {

	public SpringBootServiceImplementationGenerator(IJavaProject project, SystemObject systemObject) {
		super(project, systemObject);
	}

	@Override
	protected List<String> getFrameworkImports() {
		return Arrays.asList(
			"org.springframework.beans.factory.annotation.Autowired",
			"org.springframework.stereotype.Component"
		);
	}

	@Override
	protected String getClassAnnotation() {
		return "@Component";
	}

	@Override
	protected String getEntityManagerFieldAnnotation() {
		return "@Autowired";
	}
}
