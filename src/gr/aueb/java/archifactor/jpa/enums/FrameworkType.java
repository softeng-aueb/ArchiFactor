package gr.aueb.java.archifactor.jpa.enums;

public enum FrameworkType {
	QUARKUS("Quarkus"),
	SPRING_BOOT("Spring Boot");

	private final String displayName;

	FrameworkType(String displayName) {
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
