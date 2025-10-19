package gr.aueb.java.archifactor.jpa.enums;

public enum JpaRelationshipType {
    ONE_TO_MANY("OneToMany"),
    MANY_TO_ONE("ManyToOne"),
    ONE_TO_ONE("OneToOne"),
    MANY_TO_MANY("ManyToMany");

    private final String annotationName;

    JpaRelationshipType(String annotationName) {
        this.annotationName = annotationName;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public static JpaRelationshipType fromAnnotationName(String annotationName) {
        for (JpaRelationshipType type : values()) {
            if (type.annotationName.equals(annotationName)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isRelationshipType(String annotationName) {
        return fromAnnotationName(annotationName) != null;
    }
}
