package gr.aueb.java.ddd.aggregatesIdentification;

public enum ClusteringAlgorithm {
	LOUVAIN("Louvain"),
	UNION_FIND("Union-Find");

	private final String displayName;

	ClusteringAlgorithm(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
