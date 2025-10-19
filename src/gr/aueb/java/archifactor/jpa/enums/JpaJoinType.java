package gr.aueb.java.archifactor.jpa.enums;

public enum JpaJoinType {
    JOIN_COLUMN("JoinColumn"),
    JOIN_TABLE("JoinTable");

    private final String annotationName;

    JpaJoinType(String annotationName) {
        this.annotationName = annotationName;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public static JpaJoinType fromAnnotationName(String annotationName) {
        for (JpaJoinType type : values()) {
            if (type.annotationName.equals(annotationName)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isJoinType(String annotationName) {
        return fromAnnotationName(annotationName) != null;
    }
}
