package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.core.resources.IFile;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ASTReader;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class BaseServiceImplementationGenerator {
	protected IJavaProject project;
	protected SystemObject systemObject;

	public BaseServiceImplementationGenerator(IJavaProject project, SystemObject systemObject) {
		this.project = project;
		this.systemObject = systemObject;
	}

	protected abstract List<String> getFrameworkImports();
	protected abstract String getClassAnnotation();
	protected abstract String getEntityManagerFieldAnnotation();

	public Change createOrUpdateServiceImplementation(String entityName, List<ServiceMethodRequirement> requirements, String servicePackage) throws JavaModelException {
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		String serviceImplName = simpleEntityName + "ServiceImpl";
		String serviceImplFileName = serviceImplName + ".java";

		IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
		if (packageFragment == null) {
			return null;
		}

		ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceImplFileName);
		if (existingCU.exists()) {
			return updateExistingServiceImplementation(existingCU, entityName, requirements);
		}

		// Create the service interface content
		String serviceImplContent = generateServiceImplementationContent(entityName, requirements, servicePackage);

		// Create empty compilation unit first
		ICompilationUnit newCU = packageFragment.createCompilationUnit(serviceImplFileName, "", false, null);
		IFile file = (IFile) newCU.getResource();

		// Create a change that replaces the empty content with the interface content
		TextFileChange change = new TextFileChange("Create " + serviceImplName + " implementation", file);
		change.setEdit(new ReplaceEdit(0, 0, serviceImplContent));
		return change;
	}

	private Change updateExistingServiceImplementation(ICompilationUnit existingCU, String entityName, List<ServiceMethodRequirement> requirements) throws JavaModelException, IllegalArgumentException {
		ASTParser parser = ASTParser.newParser(ASTReader.JLS);
		parser.setSource(existingCU);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		Set<String> existingMethodSignatures = extractExistingMethodSignatures(astRoot);
		List<ServiceMethodRequirement> missingMethods = findMissingMethods(requirements, existingMethodSignatures);
		if (missingMethods.isEmpty()) {
			return null;
		}

		ASTRewrite rewriter = ASTRewrite.create(astRoot.getAST());
		AST ast = astRoot.getAST();
		TypeDeclaration typeDecl = (TypeDeclaration) astRoot.types().get(0);
		ListRewrite methodsRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

		Set<String> neededImports = new TreeSet<>();
		for (ServiceMethodRequirement requirement : missingMethods) {
			if (requirement.getReturnType().contains("List")) {
				neededImports.add("java.util.List");
			}
			if (requirement.getMethodType() == ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING) {
				neededImports.add("java.util.Collection");
			}
		}

		ListRewrite importsRewrite = rewriter.getListRewrite(astRoot, CompilationUnit.IMPORTS_PROPERTY);
		Set<String> existingImports = extractExistingImports(astRoot);
		for (String importName : neededImports) {
			if (!existingImports.contains(importName)) {
				ImportDeclaration importDecl = ast.newImportDeclaration();
				importDecl.setName(ast.newName(importName));
				importsRewrite.insertLast(importDecl, new TextEditGroup("Add missing import"));
			}
		}

		for (ServiceMethodRequirement requirement : missingMethods) {
			MethodDeclaration methodDecl = generateMethodImplementation(ast, requirement, entityName);
			methodsRewrite.insertLast(methodDecl, new TextEditGroup("Add missing service method implementation"));
		}

		IFile file = (IFile) existingCU.getResource();
		TextFileChange change = new TextFileChange("Update " + existingCU.getElementName(), file);
		change.setEdit(rewriter.rewriteAST());
		return change;
	}

	private Set<String> extractExistingImports(CompilationUnit astRoot) {
		Set<String> imports = new HashSet<>();
		for (Object obj : astRoot.imports()) {
			if (obj instanceof ImportDeclaration) {
				ImportDeclaration importDecl = (ImportDeclaration) obj;
				imports.add(importDecl.getName().getFullyQualifiedName());
			}
		}
		return imports;
	}

	private Set<String> extractExistingMethodSignatures(CompilationUnit astRoot) {
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

	private String buildMethodSignature(MethodDeclaration method) {
		String methodName = method.getName().getIdentifier();

		if (method.parameters().isEmpty()) {
			return methodName + "()";
		}

		SingleVariableDeclaration param = (SingleVariableDeclaration) method.parameters().get(0);
		String paramType = param.getType().toString();
		String paramName = param.getName().getIdentifier();
		return methodName + "(" + paramType + " " + paramName + ")";
	}

	private List<ServiceMethodRequirement> findMissingMethods(List<ServiceMethodRequirement> requirements, Set<String> existingSignatures) {
		List<ServiceMethodRequirement> missing = new ArrayList<>();
		for (ServiceMethodRequirement requirement : requirements) {
			if (!existingSignatures.contains(requirement.getMethodSignature())) {
				missing.add(requirement);
			}
		}
		return missing;
	}

	protected String generateServiceImplementationContent(String entityName, List<ServiceMethodRequirement> requirements, String packageName) {
		Set<String> imports = new TreeSet<>();

		StringBuilder content = new StringBuilder();
		content.append("package ").append(packageName).append(";\n\n");

		String entityPackage = UnmapJpaRelationshipsUtils.determineEntityPackage(systemObject, entityName);
		if (entityPackage != null && !entityPackage.equals(packageName)) {
			imports.add(entityName);
		}

		imports.addAll(getFrameworkImports());
		imports.add("jakarta.persistence.EntityManager");

		for (ServiceMethodRequirement requirement : requirements) {
			if (requirement.getReturnType().contains("List")) {
				imports.add("java.util.List");
			}
			if (requirement.getMethodType() == ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING) {
				imports.add("java.util.Collection");
			}
		}

		for (String imp : imports) {
			content.append("import ").append(imp).append(";\n");
		}

		content.append("\n").append(getClassAnnotation()).append("\n");
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		content.append("public class ").append(simpleEntityName).append("ServiceImpl implements ").append(simpleEntityName).append("Service {\n");
		content.append("    ").append(getEntityManagerFieldAnnotation()).append("\n");
		content.append("    private EntityManager entityManager;\n\n");

		for (ServiceMethodRequirement requirement : requirements) {
			content.append(generateMethodImplementationString(requirement, entityName));
			content.append("\n");
		}

		content.append("}\n");
		return content.toString();
	}

	private String generateMethodImplementationString(ServiceMethodRequirement requirement, String entityName) {
		StringBuilder impl = new StringBuilder();
		impl.append("    @Override\n");
		impl.append("    public ").append(requirement.getReturnType()).append(" ").append(requirement.getMethodName()).append("(");

		String paramName = requirement.getParameterName();
		impl.append(requirement.getParameterTypeString()).append(" ").append(paramName).append(") {\n");

		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
		switch (requirement.getMethodType()) {
			case GET_BY_ID:
				impl.append("        return entityManager.find(").append(simpleEntityName).append(".class, ").append(paramName).append(");\n");
				break;
			case GET_BY_FOREIGN_KEY:
				String paramType = requirement.getParameterType();
				boolean isPrimitive = paramType.equals("int") || paramType.equals("long");
				if (!isPrimitive) {
					impl.append("        if (").append(paramName).append(" == null) {\n");
					impl.append("            return ").append(getEmptyReturnValue(requirement.getReturnType())).append(";\n");
					impl.append("        }\n");
				}
				impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(simpleEntityName).append(" e WHERE e.")
				   .append(requirement.getForeignKeyFieldName()).append(" = :").append(paramName).append("\", ").append(simpleEntityName).append(".class)\n");
				impl.append("                .setParameter(\"").append(paramName).append("\", ").append(paramName).append(")\n");
				impl.append("                .getResultList();\n");
				break;
			case GET_BY_MANY_TO_MANY_OWNING:
				impl.append("        if (").append(paramName).append(" == null || ").append(paramName).append(".isEmpty()) {\n");
				impl.append("            return ").append(getEmptyReturnValue(requirement.getReturnType())).append(";\n");
				impl.append("        }\n");
				impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(simpleEntityName).append(" e WHERE e.")
				   .append(requirement.getToEntityIdFieldName()).append(" IN :ids\", ")
				   .append(simpleEntityName).append(".class)\n");
				impl.append("                .setParameter(\"ids\", ").append(paramName).append(")\n");
				impl.append("                .getResultList();\n");
				break;
			case GET_BY_MANY_TO_MANY_NON_OWNING:
				String joinFieldName = requirement.getJoinTableInverseJoinColumns() + "s";
				impl.append("        if (").append(paramName).append(" == null) {\n");
				impl.append("            return ").append(getEmptyReturnValue(requirement.getReturnType())).append(";\n");
				impl.append("        }\n");
				impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(simpleEntityName)
				   .append(" e JOIN e.").append(joinFieldName).append(" ec WHERE ec = :id\", ")
				   .append(simpleEntityName).append(".class)\n");
				impl.append("                .setParameter(\"id\", ").append(paramName).append(")\n");
				impl.append("                .getResultList();\n");
				break;
		}

		impl.append("    }\n");
		return impl.toString();
	}

	private String getEmptyReturnValue(String returnType) {
		if (returnType.contains("List")) {
			return "List.of()";
		}
		return "null";
	}

	private MethodDeclaration generateMethodImplementation(AST ast, ServiceMethodRequirement requirement, String entityName) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(requirement.getMethodName()));

		MarkerAnnotation overrideAnnotation = ast.newMarkerAnnotation();
		overrideAnnotation.setTypeName(ast.newName("Override"));
		method.modifiers().add(overrideAnnotation);
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

		Type returnType = createTypeFromString(ast, requirement.getReturnType());
		method.setReturnType2(returnType);

		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		Type paramType = createTypeFromString(ast, requirement.getParameterTypeString());
		param.setType(paramType);
		param.setName(ast.newSimpleName(requirement.getParameterName()));
		method.parameters().add(param);

		Block body = createMethodBody(ast, requirement, entityName);
		method.setBody(body);
		return method;
	}

	private Block createMethodBody(AST ast, ServiceMethodRequirement requirement, String entityName) {
		Block body = ast.newBlock();
		String paramName = requirement.getParameterName();
		String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);

		switch (requirement.getMethodType()) {
			case GET_BY_ID:
				createGetByIdBody(ast, body, simpleEntityName, paramName);
				break;
			case GET_BY_FOREIGN_KEY:
				createForeignKeyQueryBody(ast, body, simpleEntityName, paramName, requirement.getForeignKeyFieldName(), requirement.getParameterType());
				break;
			case GET_BY_MANY_TO_MANY_OWNING:
				createManyToManyOwningQueryBody(ast, body, simpleEntityName, paramName, requirement.getToEntityIdFieldName());
				break;
			case GET_BY_MANY_TO_MANY_NON_OWNING:
				createManyToManyNonOwningQueryBody(ast, body, simpleEntityName, requirement.getJoinTableInverseJoinColumns(), paramName);
				break;
		}

		return body;
	}

	private void createGetByIdBody(AST ast, Block body, String entityName, String paramName) {
		MethodInvocation findCall = ast.newMethodInvocation();
		findCall.setExpression(ast.newSimpleName("entityManager"));
		findCall.setName(ast.newSimpleName("find"));

		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(entityName)));
		findCall.arguments().add(typeLiteral);
		findCall.arguments().add(ast.newSimpleName(paramName));

		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression(findCall);
		body.statements().add(returnStmt);
	}

	private void createForeignKeyQueryBody(AST ast, Block body, String entityName, String paramName, String fkFieldName, String paramType) {
		boolean isPrimitive = paramType.equals("int") || paramType.equals("long");
		if (!isPrimitive) {
			InfixExpression nullCheck = ast.newInfixExpression();
			nullCheck.setLeftOperand(ast.newSimpleName(paramName));
			nullCheck.setOperator(InfixExpression.Operator.EQUALS);
			nullCheck.setRightOperand(ast.newNullLiteral());

			IfStatement nullCheckIf = ast.newIfStatement();
			nullCheckIf.setExpression(nullCheck);

			Block thenBlock = ast.newBlock();
			ReturnStatement emptyReturn = ast.newReturnStatement();
			MethodInvocation listOf = ast.newMethodInvocation();
			listOf.setExpression(ast.newName("List"));
			listOf.setName(ast.newSimpleName("of"));
			emptyReturn.setExpression(listOf);
			thenBlock.statements().add(emptyReturn);
			nullCheckIf.setThenStatement(thenBlock);
			body.statements().add(nullCheckIf);
		}

		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, entityName, "SELECT e FROM " + entityName + " e WHERE e." + fkFieldName + " = :" + paramName, paramName, paramName));
		body.statements().add(queryReturn);
	}

	private void createManyToManyOwningQueryBody(AST ast, Block body, String entityName, String paramName, String idFieldName) {
		InfixExpression nullCheck = ast.newInfixExpression();
		nullCheck.setLeftOperand(ast.newSimpleName(paramName));
		nullCheck.setOperator(InfixExpression.Operator.EQUALS);
		nullCheck.setRightOperand(ast.newNullLiteral());

		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression(ast.newSimpleName(paramName));
		isEmptyCall.setName(ast.newSimpleName("isEmpty"));

		InfixExpression combinedCheck = ast.newInfixExpression();
		combinedCheck.setLeftOperand(nullCheck);
		combinedCheck.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
		combinedCheck.setRightOperand(isEmptyCall);

		IfStatement checkIf = ast.newIfStatement();
		checkIf.setExpression(combinedCheck);

		Block thenBlock = ast.newBlock();
		ReturnStatement emptyReturn = ast.newReturnStatement();
		MethodInvocation listOf = ast.newMethodInvocation();
		listOf.setExpression(ast.newName("List"));
		listOf.setName(ast.newSimpleName("of"));
		emptyReturn.setExpression(listOf);
		thenBlock.statements().add(emptyReturn);
		checkIf.setThenStatement(thenBlock);
		body.statements().add(checkIf);

		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, entityName, "SELECT e FROM " + entityName + " e WHERE e." + idFieldName + " IN :ids", "ids", paramName));
		body.statements().add(queryReturn);
	}

	private void createManyToManyNonOwningQueryBody(AST ast, Block body, String entityName, String joinTableInverseJoinColumns, String paramName) {
		InfixExpression nullCheck = ast.newInfixExpression();
		nullCheck.setLeftOperand(ast.newSimpleName(paramName));
		nullCheck.setOperator(InfixExpression.Operator.EQUALS);
		nullCheck.setRightOperand(ast.newNullLiteral());

		IfStatement nullCheckIf = ast.newIfStatement();
		nullCheckIf.setExpression(nullCheck);

		Block thenBlock = ast.newBlock();
		ReturnStatement emptyReturn = ast.newReturnStatement();
		MethodInvocation listOf = ast.newMethodInvocation();
		listOf.setExpression(ast.newName("List"));
		listOf.setName(ast.newSimpleName("of"));
		emptyReturn.setExpression(listOf);
		thenBlock.statements().add(emptyReturn);
		nullCheckIf.setThenStatement(thenBlock);
		body.statements().add(nullCheckIf);

		String joinFieldName = joinTableInverseJoinColumns + "s";
		String jpql = "SELECT e FROM " + entityName + " e JOIN e." + joinFieldName + " ec WHERE ec = :id";
		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, entityName, jpql, "id", paramName));
		body.statements().add(queryReturn);
	}

	private Expression createJPQLQuery(AST ast, String entityName, String jpql, String paramName, String paramValue) {
		MethodInvocation createQuery = ast.newMethodInvocation();
		createQuery.setExpression(ast.newSimpleName("entityManager"));
		createQuery.setName(ast.newSimpleName("createQuery"));

		StringLiteral queryString = ast.newStringLiteral();
		queryString.setLiteralValue(jpql);
		createQuery.arguments().add(queryString);

		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(entityName)));
		createQuery.arguments().add(typeLiteral);

		MethodInvocation setParameter = ast.newMethodInvocation();
		setParameter.setExpression(createQuery);
		setParameter.setName(ast.newSimpleName("setParameter"));

		StringLiteral paramNameLiteral = ast.newStringLiteral();
		paramNameLiteral.setLiteralValue(paramName);
		setParameter.arguments().add(paramNameLiteral);
		setParameter.arguments().add(ast.newSimpleName(paramValue));

		MethodInvocation getResultList = ast.newMethodInvocation();
		getResultList.setExpression(setParameter);
		getResultList.setName(ast.newSimpleName("getResultList"));

		return getResultList;
	}

	private Type createTypeFromString(AST ast, String typeString) {
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
