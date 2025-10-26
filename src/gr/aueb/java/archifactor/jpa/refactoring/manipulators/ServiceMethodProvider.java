package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import java.util.Set;

/**
 * Provider interface for generating service methods for the different JPA relationship types.
 * Each provider encapsulates all the knowledge about how to generate a specific type of service method.
 */
public interface ServiceMethodProvider {
	String getToEntityName();

	String getMethodName();

	String getReturnType();

	String getParameterName();

	String getParameterTypeString();

	String getMethodSignature();

	String getMethodDeclaration();

	String generateMethodImplementationString();

	Block createMethodBody(AST ast);

	Set<String> getRequiredImports();

	boolean equals(Object obj);

	int hashCode();
}
