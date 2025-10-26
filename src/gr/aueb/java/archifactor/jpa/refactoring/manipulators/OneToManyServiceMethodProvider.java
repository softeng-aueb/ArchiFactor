package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.jdt.core.dom.*;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import java.util.Set;

public class OneToManyServiceMethodProvider extends AbstractServiceMethodProvider {
	private final String fromEntityIdTypeSimple;
	private final String fkFieldName;

	private OneToManyServiceMethodProvider(String toEntity, String fromEntityIdTypeSimple, String fkFieldName, String methodName) {
		super(toEntity, methodName);
		this.fromEntityIdTypeSimple = fromEntityIdTypeSimple;
		this.fkFieldName = fkFieldName;
	}

	public static OneToManyServiceMethodProvider fromRelationship(RelationshipInfo relationship) {
		String toEntity = relationship.getToEntity();
		String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);

		String fromEntityIdType = relationship.getOriginPkType();
		String fromEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(fromEntityIdType);

		String fkFieldName = relationship.getJoinColumnName();
		String methodName = "get" + toEntitySimple + "sBy" + UnmapJpaRelationshipsUtils.capitalize(fkFieldName);

		return new OneToManyServiceMethodProvider(toEntity, fromEntityIdTypeSimple, fkFieldName, methodName);
	}

	@Override
	public String getReturnType() {
		return "List<" + toEntitySimple + ">";
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
			impl.append("            return List.of();\n");
			impl.append("        }\n");
		}

		impl.append("        return entityManager.createQuery(\"SELECT e FROM ").append(toEntitySimple)
		    .append(" e WHERE e.").append(fkFieldName).append(" = :").append(fkFieldName).append("\", ")
			.append(toEntitySimple).append(".class)\n");
		impl.append("                .setParameter(\"").append(fkFieldName).append("\", ").append(fkFieldName).append(")\n");
		impl.append("                .getResultList();\n");
		impl.append("    }\n");
		return impl.toString();
	}

	@Override
	public Block createMethodBody(AST ast) {
		Block body = ast.newBlock();

		boolean isPrimitive = fromEntityIdTypeSimple.equals("int") || fromEntityIdTypeSimple.equals("long");
		if (!isPrimitive) {
			addNullCheckWithEmptyListReturn(ast, body, fkFieldName);
		}

		String jpql = "SELECT e FROM " + toEntitySimple + " e WHERE e." + fkFieldName + " = :" + fkFieldName;
		ReturnStatement queryReturn = ast.newReturnStatement();
		queryReturn.setExpression(createJPQLQuery(ast, jpql, fkFieldName, fkFieldName));
		body.statements().add(queryReturn);
		return body;
	}

	@Override
	public Set<String> getRequiredImports() {
		return Set.of("java.util.List");
	}
}
