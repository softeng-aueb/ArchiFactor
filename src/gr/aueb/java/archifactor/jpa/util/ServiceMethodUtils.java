package gr.aueb.java.archifactor.jpa.util;

import org.eclipse.jdt.core.dom.*;

import gr.aueb.java.archifactor.jpa.refactoring.manipulators.ServiceMethodProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServiceMethodUtils {

	public static String buildMethodSignature(MethodDeclaration method) {
		String methodName = method.getName().getIdentifier();
		if (method.parameters().isEmpty()) {
			return methodName + "()";
		}

		SingleVariableDeclaration param = (SingleVariableDeclaration) method.parameters().get(0);
		String paramType = param.getType().toString();
		String paramName = param.getName().getIdentifier();
		return methodName + "(" + paramType + " " + paramName + ")";
	}

	public static List<ServiceMethodProvider> findMissingMethods(List<ServiceMethodProvider> providers, Set<String> existingSignatures) {
		List<ServiceMethodProvider> missing = new ArrayList<>();
		for (ServiceMethodProvider provider : providers) {
			if (!existingSignatures.contains(provider.getMethodSignature())) {
				missing.add(provider);
			}
		}
		return missing;
	}

	public static Set<String> extractExistingMethodSignatures(CompilationUnit astRoot) {
		Set<String> signatures = new HashSet<>();
		if (astRoot.types().isEmpty()) {
			return signatures;
		}

		Object firstType = astRoot.types().get(0);
		if (!(firstType instanceof TypeDeclaration)) {
			return signatures;
		}

		TypeDeclaration typeDecl = (TypeDeclaration) firstType;
		for (MethodDeclaration method : typeDecl.getMethods()) {
			String signature = buildMethodSignature(method);
			signatures.add(signature);
		}
		return signatures;
	}

	public static Set<String> extractExistingImports(CompilationUnit astRoot) {
		Set<String> imports = new HashSet<>();
		for (Object obj : astRoot.imports()) {
			if (obj instanceof ImportDeclaration) {
				ImportDeclaration importDecl = (ImportDeclaration) obj;
				imports.add(importDecl.getName().getFullyQualifiedName());
			}
		}
		return imports;
	}

	public static Type createTypeFromString(AST ast, String typeString) {
		if (typeString.contains("<") && typeString.contains(">")) {
			String[] parts = typeString.split("[<>]");
			String containerType = parts[0];
			String elementType = parts[1];
			ParameterizedType paramType = ast.newParameterizedType(ast.newSimpleType(ast.newName(containerType)));
			paramType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));
			return paramType;
		}
		return ast.newSimpleType(ast.newName(typeString));
	}
}
