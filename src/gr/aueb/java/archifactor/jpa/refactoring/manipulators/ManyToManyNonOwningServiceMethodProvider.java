package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import java.util.Set;

public class ManyToManyNonOwningServiceMethodProvider extends AbstractServiceMethodProvider {
	private final String fromEntityIdTypeSimple;
	private final String joinFieldName;

	private ManyToManyNonOwningServiceMethodProvider(String toEntity, String fromEntityIdTypeSimple, String joinTableInverseJoinColumns, String methodName) {
		super(toEntity, methodName);
		this.fromEntityIdTypeSimple = fromEntityIdTypeSimple;
		this.joinFieldName = joinTableInverseJoinColumns + "s";
	}

	public static ManyToManyNonOwningServiceMethodProvider fromRelationship(RelationshipInfo relationship) {
		String toEntity = relationship.getToEntity();
		String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
		String fromEntity = relationship.getFromEntity();
		String fromEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(fromEntity);

		String fromEntityIdType = relationship.getOriginPkType();
		String fromEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(fromEntityIdType);

		String methodName = "get" + toEntitySimple + "sBy" + fromEntitySimple + "Id";
		String joinTableInverseJoinColumns = relationship.getJoinTableInverseJoinColumns();

		return new ManyToManyNonOwningServiceMethodProvider(toEntity, fromEntityIdTypeSimple, joinTableInverseJoinColumns, methodName);
	}

	@Override
	public String getReturnType() {
		return "List<" + toEntitySimple + ">";
	}

	@Override
	public String getParameterName() {
		return "id";
	}

	@Override
	public String getParameterTypeString() {
		return fromEntityIdTypeSimple;
	}

	@Override
	public String getMethodSignature() {
		return methodName + "(" + fromEntityIdTypeSimple + " id)";
	}

	@Override
	public String generateMethodImplementationString() {
		StringBuilder impl = new StringBuilder();
		impl.append("    @Override\n");
		impl.append("    public ").append(getReturnType()).append(" ").append(methodName)
		    .append("(").append(getParameterTypeString()).append(" ").append(getParameterName()).append(") {\n");
		impl.append("        if (id == null) {\n");
		impl.append("            return List.of();\n");
		impl.append("        }\n");
		impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(toEntitySimple)
		    .append(" e JOIN e.").append(joinFieldName).append(" ec WHERE ec = :id\", ")
			.append(toEntitySimple).append(".class)\n");
		impl.append("                .setParameter(\"id\", id)\n");
		impl.append("                .getResultList();\n");
		impl.append("    }\n");
		return impl.toString();
	}

	@Override
	public Block createMethodBody(AST ast) {
		Block body = ast.newBlock();

		addNullCheckWithEmptyListReturn(ast, body, "id");

		String jpql = "SELECT e FROM " + toEntitySimple + " e JOIN e." + joinFieldName + " ec WHERE ec = :id";
		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, jpql, "id", "id"));
		body.statements().add(queryReturn);
		return body;
	}

	@Override
	public Set<String> getRequiredImports() {
		return Set.of("java.util.List");
	}
}
