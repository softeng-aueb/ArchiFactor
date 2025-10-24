package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.*;

import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.uom.java.ast.SystemObject;

import java.util.Set;

public class SpringBootServiceFactoryGenerator extends BaseServiceFactoryGenerator {

	public SpringBootServiceFactoryGenerator(IJavaProject project, SystemObject systemObject) {
		super(project, systemObject);
	}

	@Override
	protected String generateServiceFactoryContent(Set<String> entityNames, String packageName) {
		StringBuilder content = new StringBuilder();
		content.append("package ").append(packageName).append(";\n\n");
		content.append("import org.springframework.beans.BeansException;\n");
		content.append("import org.springframework.context.ApplicationContext;\n");
		content.append("import org.springframework.context.ApplicationContextAware;\n");
		content.append("import org.springframework.stereotype.Component;\n");
		content.append("\n");
		for (String entityName : entityNames) {
			String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
			String serviceInterface = UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)) + "." + simpleEntityName + "Service";
			content.append("import ").append(serviceInterface).append(";\n");
		}
		content.append("\n");
		content.append("@Component\n");
		content.append("public class ServiceFactory implements ApplicationContextAware {\n");
		content.append("    private static ApplicationContext applicationContext;\n\n");
		content.append("    @Override\n");
		content.append("    public void setApplicationContext(ApplicationContext context) throws BeansException {\n");
		content.append("        applicationContext = context;\n");
		content.append("    }\n\n");
		content.append("    public static boolean isContainerAvailable() {\n");
		content.append("        return applicationContext != null;\n");
		content.append("    }\n\n");
		for (String entityName : entityNames) {
			String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
			String serviceName = simpleEntityName + "Service";
			String methodName = "get" + serviceName;
			content.append("    public static ").append(serviceName).append(" ").append(methodName).append("() {\n");
			content.append("        return applicationContext.getBean(").append(serviceName).append(".class);\n");
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
		MethodInvocation getBeanCall = createGetBeanCall(ast, serviceName);
		returnStatement.setExpression(getBeanCall);
		return returnStatement;
	}

	private MethodInvocation createGetBeanCall(AST ast, String serviceName) {
		MethodInvocation getBeanCall = ast.newMethodInvocation();
		getBeanCall.setExpression(ast.newName("applicationContext"));
		getBeanCall.setName(ast.newSimpleName("getBean"));

		TypeLiteral classLiteral = ast.newTypeLiteral();
		classLiteral.setType(ast.newSimpleType(ast.newName(serviceName)));
		getBeanCall.arguments().add(classLiteral);
		return getBeanCall;
	}
}
