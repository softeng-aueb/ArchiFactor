package gr.aueb.java.ddd.aggregatesIdentification;

public enum EdgeType {
    EMBEDDED,   // Value objects that are intrinsic parts of an entity
    COUPLED,  // Entities that are owned by the parent
    REFERENCE   // Loose, cross-aggregate references
}
