package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.*;

import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.uom.java.ast.SystemObject;

import java.util.Set;

public class QuarkusServiceFactoryGenerator extends BaseServiceFactoryGenerator {

	public QuarkusServiceFactoryGenerator(IJavaProject project, SystemObject systemObject) {
		super(project, systemObject);
	}

	@Override
	protected String generateServiceFactoryContent(Set<String> entityNames, String packageName) {
		StringBuilder content = new StringBuilder();
		content.append("package ").append(packageName).append(";\n\n");
		content.append("import jakarta.enterprise.inject.spi.CDI;\n");
		content.append("\n");
		for (String entityName : entityNames) {
			String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
			String serviceInterface = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)) + "." + simpleEntityName + "Service";
			content.append("import ").append(serviceInterface).append(";\n");
		}
		content.append("\n");
		content.append("public class ServiceFactory {\n\n");
		content.append("    public static boolean isContainerAvailable() {\n");
		content.append("        try {\n");
		content.append("            CDI.current();\n");
		content.append("            return true;\n");
		content.append("        } catch (IllegalStateException e) {\n");
		content.append("            return false;\n");
		content.append("        }\n");
		content.append("    }\n\n");
		for (String entityName : entityNames) {
			String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
			String serviceName = simpleEntityName + "Service";
			String methodName = "get" + serviceName;
			content.append("    public static ").append(serviceName).append(" ").append(methodName).append("() {\n");
			content.append("        return CDI.current().select(").append(serviceName).append(".class).get();\n");
			content.append("    }\n\n");
		}
		content.append("}\n");
		return content.toString();
	}

	@Override
	protected MethodDeclaration createServiceGetterMethod(AST ast, String entityName) {
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		String serviceName = simpleEntityName + "Service";
		String methodName = "get" + serviceName;

		MethodDeclaration method = createMethodDeclaration(ast, methodName, serviceName);
		Block methodBody = createMethodBody(ast, serviceName);
		method.setBody(methodBody);
		return method;
	}

	private MethodDeclaration createMethodDeclaration(AST ast, String methodName, String returnTypeName) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(methodName));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		method.setReturnType2(ast.newSimpleType(ast.newName(returnTypeName)));
		return method;
	}

	private Block createMethodBody(AST ast, String serviceName) {
		Block methodBody = ast.newBlock();
		ReturnStatement returnStatement = createReturnStatement(ast, serviceName);
		methodBody.statements().add(returnStatement);
		return methodBody;
	}

	private ReturnStatement createReturnStatement(AST ast, String serviceName) {
		ReturnStatement returnStatement = ast.newReturnStatement();
		MethodInvocation cdiSelectGetCall = createCdiSelectGetCall(ast, serviceName);
		returnStatement.setExpression(cdiSelectGetCall);
		return returnStatement;
	}

	private MethodInvocation createCdiSelectGetCall(AST ast, String serviceName) {
		MethodInvocation currentCall = createCdiCurrentCall(ast);
		MethodInvocation selectCall = createSelectCall(ast, currentCall, serviceName);

		MethodInvocation getCall = ast.newMethodInvocation();
		getCall.setName(ast.newSimpleName("get"));
		getCall.setExpression(selectCall);

		return getCall;
	}

	private MethodInvocation createCdiCurrentCall(AST ast) {
		MethodInvocation currentCall = ast.newMethodInvocation();
		currentCall.setExpression(ast.newName("CDI"));
		currentCall.setName(ast.newSimpleName("current"));
		return currentCall;
	}

	private MethodInvocation createSelectCall(AST ast, MethodInvocation currentCall, String serviceName) {
		MethodInvocation selectCall = ast.newMethodInvocation();
		selectCall.setName(ast.newSimpleName("select"));
		selectCall.setExpression(currentCall);

		TypeLiteral classLiteral = ast.newTypeLiteral();
		classLiteral.setType(ast.newSimpleType(ast.newName(serviceName)));
		selectCall.arguments().add(classLiteral);
		return selectCall;
	}
}
