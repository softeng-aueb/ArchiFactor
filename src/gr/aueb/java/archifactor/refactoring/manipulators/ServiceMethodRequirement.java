package gr.aueb.java.archifactor.refactoring.manipulators;

public class ServiceMethodRequirement {
    private String toEntityName;
    private String fromEntitySimpleName;
    private ServiceMethodType methodType;
    private String parameterType;
    private String returnType;
    private String foreignKeyFieldName;
    private String methodName;
    private String toEntityIdFieldName;
    private String joinTableInverseJoinColumns;

    public ServiceMethodRequirement(String toEntityName, String fromEntitySimpleName, ServiceMethodType methodType,
                                    String parameterType, String returnType, String foreignKeyFieldName, String methodName,
                                    String toEntityIdFieldName, String joinTableInverseJoinColumns) {
        this.toEntityName = toEntityName;
        this.fromEntitySimpleName = fromEntitySimpleName;
        this.methodType = methodType;
        this.parameterType = parameterType;
        this.returnType = returnType;
        this.foreignKeyFieldName = foreignKeyFieldName;
        this.methodName = methodName;
        this.toEntityIdFieldName = toEntityIdFieldName;
        this.joinTableInverseJoinColumns = joinTableInverseJoinColumns;
    }

    public String getToEntityName() {
        return toEntityName;
    }

    public String getFromEntitySimpleName() {
        return fromEntitySimpleName;
    }

    public ServiceMethodType getMethodType() {
        return methodType;
    }

    public String getParameterType() {
        return parameterType;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getForeignKeyFieldName() {
        return foreignKeyFieldName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getToEntityIdFieldName() {
        return toEntityIdFieldName;
    }

    public String getJoinTableInverseJoinColumns() {
        return joinTableInverseJoinColumns;
    }

    public String getMethodSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append("(");

        switch (methodType) {
            case GET_BY_ID:
                sb.append(parameterType).append(" id");
                break;
            case GET_BY_FOREIGN_KEY:
                sb.append(parameterType).append(" ").append(foreignKeyFieldName);
                break;
            case GET_BY_MANY_TO_MANY_OWNING:
                sb.append("Collection<").append(parameterType).append("> ids");
                break;
            case GET_BY_MANY_TO_MANY_NON_OWNING:
                sb.append(parameterType).append(" id");
                break;
        }

        sb.append(")");
        return sb.toString();
    }

    public String getMethodDeclaration() {
        return returnType + " " + getMethodSignature();
    }

    public String getParameterName() {
        switch (methodType) {
            case GET_BY_ID:
                return "id";
            case GET_BY_FOREIGN_KEY:
                return foreignKeyFieldName;
            case GET_BY_MANY_TO_MANY_OWNING:
                return "ids";
            case GET_BY_MANY_TO_MANY_NON_OWNING:
                return "id";
            default:
                return "param";
        }
    }

    public String getParameterTypeString() {
        if (methodType == ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING) {
            return "Collection<" + parameterType + ">";
        }
        return parameterType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ServiceMethodRequirement other = (ServiceMethodRequirement) obj;
        return getMethodSignature().equals(other.getMethodSignature());
    }

    @Override
    public int hashCode() {
        return getMethodSignature().hashCode();
    }
}
