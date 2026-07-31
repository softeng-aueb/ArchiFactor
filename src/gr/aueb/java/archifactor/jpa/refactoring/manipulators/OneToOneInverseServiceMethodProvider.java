package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import java.util.Set;

public class OneToOneInverseServiceMethodProvider extends AbstractServiceMethodProvider {
	private final String fromEntityIdTypeSimple;
	private final String fkFieldName;

	private OneToOneInverseServiceMethodProvider(String toEntity, String fromEntityIdTypeSimple, String fkFieldName, String methodName) {
		super(toEntity, methodName);
		this.fromEntityIdTypeSimple = fromEntityIdTypeSimple;
		this.fkFieldName = fkFieldName;
	}

	public static OneToOneInverseServiceMethodProvider fromRelationship(RelationshipInfo relationship) {
		String toEntity = relationship.getToEntity();
		String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);

		String fromEntityIdType = relationship.getOriginPkType();
		String fromEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(fromEntityIdType);

		String fkFieldName = relationship.getFkFieldName();
		String methodName = "get" + toEntitySimple + "By" + UnmapJpaRelationshipsUtils.capitalize(fkFieldName);

		return new OneToOneInverseServiceMethodProvider(toEntity, fromEntityIdTypeSimple, fkFieldName, methodName);
	}

	@Override
	public String getReturnType() {
		return toEntitySimple;
	}

	@Override
	public String getParameterName() {
		return fkFieldName;
	}

	@Override
	public String getParameterTypeString() {
		return fromEntityIdTypeSimple;
	}

	@Override
	public String getMethodSignature() {
		return methodName + "(" + fromEntityIdTypeSimple + " " + fkFieldName + ")";
	}

	@Override
	public String generateMethodImplementationString() {
		StringBuilder impl = new StringBuilder();
		impl.append("    @Override\n");
		impl.append("    public ").append(getReturnType()).append(" ").append(methodName)
		    .append("(").append(getParameterTypeString()).append(" ").append(getParameterName()).append(") {\n");

		boolean isPrimitive = fromEntityIdTypeSimple.equals("int") || fromEntityIdTypeSimple.equals("long");
		if (!isPrimitive) {
			impl.append("        if (").append(fkFieldName).append(" == null) {\n");
			impl.append("            return null;\n");
			impl.append("        }\n");
		}

		impl.append("        List<").append(toEntitySimple).append("> results = entityManager.createQuery(\"SELECT e FROM ")
		    .append(toEntitySimple).append(" e WHERE e.").append(fkFieldName).append(" = :").append(fkFieldName).append("\", ")
		    .append(toEntitySimple).append(".class)\n");
		impl.append("                .setParameter(\"").append(fkFieldName).append("\", ").append(fkFieldName).append(")\n");
		impl.append("                .getResultList();\n");
		impl.append("        return results.isEmpty() ? null : results.get(0);\n");
		impl.append("    }\n");
		return impl.toString();
	}

	@Override
	public Block createMethodBody(AST ast) {
		Block body = ast.newBlock();

		boolean isPrimitive = fromEntityIdTypeSimple.equals("int") || fromEntityIdTypeSimple.equals("long");
		if (!isPrimitive) {
			addNullCheckWithNullReturn(ast, body);
		}

		String jpql = "SELECT e FROM " + toEntitySimple + " e WHERE e." + fkFieldName + " = :" + fkFieldName;
		VariableDeclarationFragment resultsFragment = ast.newVariableDeclarationFragment();
		resultsFragment.setName(ast.newSimpleName("results"));
		resultsFragment.setInitializer(createJPQLQuery(ast, jpql, fkFieldName, fkFieldName));

		VariableDeclarationStatement resultsDeclaration = ast.newVariableDeclarationStatement(resultsFragment);
		ParameterizedType resultsType = ast.newParameterizedType(ast.newSimpleType(ast.newName("List")));
		resultsType.typeArguments().add(ast.newSimpleType(ast.newName(toEntitySimple)));
		resultsDeclaration.setType(resultsType);
		body.statements().add(resultsDeclaration);

		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression(ast.newSimpleName("results"));
		isEmptyCall.setName(ast.newSimpleName("isEmpty"));

		MethodInvocation firstResultCall = ast.newMethodInvocation();
		firstResultCall.setExpression(ast.newSimpleName("results"));
		firstResultCall.setName(ast.newSimpleName("get"));
		firstResultCall.arguments().add(ast.newNumberLiteral("0"));

		ConditionalExpression firstOrNull = ast.newConditionalExpression();
		firstOrNull.setExpression(isEmptyCall);
		firstOrNull.setThenExpression(ast.newNullLiteral());
		firstOrNull.setElseExpression(firstResultCall);

		ReturnStatement resultReturn = ast.newReturnStatement();
		resultReturn.setExpression(firstOrNull);
		body.statements().add(resultReturn);
		return body;
	}

	@Override
	public Set<String> getRequiredImports() {
		return Set.of("java.util.List");
	}

	private void addNullCheckWithNullReturn(AST ast, Block body) {
		InfixExpression nullCheck = ast.newInfixExpression();
		nullCheck.setLeftOperand(ast.newSimpleName(fkFieldName));
		nullCheck.setOperator(InfixExpression.Operator.EQUALS);
		nullCheck.setRightOperand(ast.newNullLiteral());

		Block thenBlock = ast.newBlock();
		ReturnStatement nullReturn = ast.newReturnStatement();
		nullReturn.setExpression(ast.newNullLiteral());
		thenBlock.statements().add(nullReturn);

		IfStatement nullCheckIf = ast.newIfStatement();
		nullCheckIf.setExpression(nullCheck);
		nullCheckIf.setThenStatement(thenBlock);
		body.statements().add(nullCheckIf);
	}
}
