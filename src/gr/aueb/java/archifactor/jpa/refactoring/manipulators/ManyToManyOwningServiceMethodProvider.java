package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import java.util.Set;

public class ManyToManyOwningServiceMethodProvider extends AbstractServiceMethodProvider {
	private final String toEntityIdTypeSimple;
	private final String toEntityIdFieldName;

	private ManyToManyOwningServiceMethodProvider(String toEntity, String toEntityIdTypeSimple, String toEntityIdFieldName, String methodName) {
		super(toEntity, methodName);
		this.toEntityIdTypeSimple = toEntityIdTypeSimple;
		this.toEntityIdFieldName = toEntityIdFieldName;
	}

	public static ManyToManyOwningServiceMethodProvider fromRelationship(RelationshipInfo relationship) {
		String toEntity = relationship.getToEntity();
		String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
		String toEntityIdType = relationship.getReferencedPkType();
		String toEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(toEntityIdType);
		String toEntityIdFieldName = relationship.getReferencedPkName();
		String methodName = "get" + toEntitySimple + "sByIds";

		return new ManyToManyOwningServiceMethodProvider(toEntity, toEntityIdTypeSimple, toEntityIdFieldName, methodName);
	}

	@Override
	public String getReturnType() {
		return "List<" + toEntitySimple + ">";
	}

	@Override
	public String getParameterName() {
		return "ids";
	}

	@Override
	public String getParameterTypeString() {
		return "Collection<" + toEntityIdTypeSimple + ">";
	}

	@Override
	public String getMethodSignature() {
		return methodName + "(" + getParameterTypeString() + " ids)";
	}

	@Override
	public String generateMethodImplementationString() {
		StringBuilder impl = new StringBuilder();
		impl.append("    @Override\n");
		impl.append("    public ").append(getReturnType()).append(" ").append(methodName)
		    .append("(").append(getParameterTypeString()).append(" ").append(getParameterName()).append(") {\n");
		impl.append("        if (ids == null || ids.isEmpty()) {\n");
		impl.append("            return List.of();\n");
		impl.append("        }\n");
		impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(toEntitySimple)
		    .append(" e WHERE e.").append(toEntityIdFieldName).append(" IN :ids\", ")
			.append(toEntitySimple).append(".class)\n");
		impl.append("                .setParameter(\"ids\", ids)\n");
		impl.append("                .getResultList();\n");
		impl.append("    }\n");
		return impl.toString();
	}

	@Override
	public Block createMethodBody(AST ast) {
		Block body = ast.newBlock();

		InfixExpression nullCheck = ast.newInfixExpression();
		nullCheck.setLeftOperand(ast.newSimpleName("ids"));
		nullCheck.setOperator(InfixExpression.Operator.EQUALS);
		nullCheck.setRightOperand(ast.newNullLiteral());

		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression(ast.newSimpleName("ids"));
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

		String jpql = "SELECT e FROM " + toEntitySimple + " e WHERE e." + toEntityIdFieldName + " IN :ids";
		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, jpql, "ids", "ids"));
		body.statements().add(queryReturn);
		return body;
	}

	@Override
	public Set<String> getRequiredImports() {
		return Set.of("java.util.Collection", "java.util.List");
	}
}
