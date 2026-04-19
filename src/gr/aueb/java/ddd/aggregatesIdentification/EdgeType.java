package gr.aueb.java.ddd.aggregatesIdentification;

public enum EdgeType {
    INHERITANCE(2.0),
    OWNERSHIP(1.0),
    COUPLED(1.0),
    REFERENCE(0.1);

    private final double baseline;

    EdgeType(double baseline) {
        this.baseline = baseline;
    }

    public double getBaseline() {
        return baseline;
    }
}
