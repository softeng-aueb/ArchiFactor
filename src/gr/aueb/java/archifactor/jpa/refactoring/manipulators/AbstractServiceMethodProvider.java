package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;

public abstract class AbstractServiceMethodProvider implements ServiceMethodProvider {
	protected final String toEntity;
	protected final String toEntitySimple;
	protected final String methodName;

	protected AbstractServiceMethodProvider(String toEntity, String methodName) {
		this.toEntity = toEntity;
		this.toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
		this.methodName = methodName;
	}

	@Override
	public String getToEntityName() {
		return toEntity;
	}

	@Override
	public String getMethodName() {
		return methodName;
	}

	@Override
	public String getMethodDeclaration() {
		return getReturnType() + " " + getMethodSignature();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ServiceMethodProvider)) return false;
		ServiceMethodProvider other = (ServiceMethodProvider) obj;
		return getMethodSignature().equals(other.getMethodSignature());
	}

	@Override
	public int hashCode() {
		return getMethodSignature().hashCode();
	}

	protected Expression createJPQLQuery(AST ast, String jpql, String paramName, String paramValue) {
		MethodInvocation createQuery = ast.newMethodInvocation();
		createQuery.setExpression(ast.newSimpleName("entityManager"));
		createQuery.setName(ast.newSimpleName("createQuery"));

		StringLiteral queryString = ast.newStringLiteral();
		queryString.setLiteralValue(jpql);
		createQuery.arguments().add(queryString);

		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(toEntitySimple)));
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

	protected void addNullCheckWithEmptyListReturn(AST ast, Block body, String paramName) {
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
}
