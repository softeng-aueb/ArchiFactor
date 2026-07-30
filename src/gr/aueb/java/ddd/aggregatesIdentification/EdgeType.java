package gr.aueb.java.ddd.aggregatesIdentification;

public enum EdgeType {
    INHERITANCE(1.5),
    IDENTITY(1.5),
    OWNERSHIP(0.75),
    CASCADE(0.4),
    REFERENCE(0.1);

    private final double baseline;

    EdgeType(double baseline) {
        this.baseline = baseline;
    }

    public double getBaseline() {
        return baseline;
    }
}
