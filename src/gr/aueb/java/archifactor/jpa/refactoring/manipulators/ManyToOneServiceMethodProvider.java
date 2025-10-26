package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import java.util.Collections;
import java.util.Set;

public class ManyToOneServiceMethodProvider extends AbstractServiceMethodProvider {
	private final String toEntityIdTypeSimple;

	private ManyToOneServiceMethodProvider(String toEntity, String toEntityIdTypeSimple, String methodName) {
		super(toEntity, methodName);
		this.toEntityIdTypeSimple = toEntityIdTypeSimple;
	}

	public static ManyToOneServiceMethodProvider fromRelationship(RelationshipInfo relationship) {
		String toEntity = relationship.getToEntity();
		String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
		String toEntityIdType = relationship.getReferencedPkType();
		String toEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(toEntityIdType);
		String methodName = "get" + toEntitySimple + "ById";

		return new ManyToOneServiceMethodProvider(toEntity, toEntityIdTypeSimple, methodName);
	}

	@Override
	public String getReturnType() {
		return toEntitySimple;
	}

	@Override
	public String getParameterName() {
		return "id";
	}

	@Override
	public String getParameterTypeString() {
		return toEntityIdTypeSimple;
	}

	@Override
	public String getMethodSignature() {
		return methodName + "(" + toEntityIdTypeSimple + " id)";
	}

	@Override
	public String generateMethodImplementationString() {
		StringBuilder impl = new StringBuilder();
		impl.append("    @Override\n");
		impl.append("    public ").append(getReturnType()).append(" ").append(methodName)
		    .append("(").append(getParameterTypeString()).append(" ").append(getParameterName()).append(") {\n");
		impl.append("        return entityManager.find(").append(toEntitySimple).append(".class, id);\n");
		impl.append("    }\n");
		return impl.toString();
	}

	@Override
	public Block createMethodBody(AST ast) {
		Block body = ast.newBlock();

		MethodInvocation findCall = ast.newMethodInvocation();
		findCall.setExpression(ast.newSimpleName("entityManager"));
		findCall.setName(ast.newSimpleName("find"));

		TypeLiteral typeLiteral = ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newName(toEntitySimple)));
		findCall.arguments().add(typeLiteral);
		findCall.arguments().add(ast.newSimpleName("id"));

		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression(findCall);
		body.statements().add(returnStmt);
		return body;
	}

	@Override
	public Set<String> getRequiredImports() {
		return Collections.emptySet();
	}
}
