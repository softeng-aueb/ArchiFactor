package gr.aueb.java.archifactor.jpa.enums;

public enum RepairHarness {
	NONE("None"),
	CLAUDE("Claude Code"),
	CODEX("Codex");

	private final String displayName;

	RepairHarness(String displayName) {
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
