package gr.aueb.java.archifactor.jpa.enums;

public enum PersistenceNamespace {
	JAKARTA("jakarta"),
	JAVAX("javax");

	private final String prefix;

	PersistenceNamespace(String prefix) {
		this.prefix = prefix;
	}

	public String getPrefix() {
		return prefix;
	}

	public String type(String nameWithoutPrefix) {
		return prefix + "." + nameWithoutPrefix;
	}
}
