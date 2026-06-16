package gr.aueb.java.archifactor.jpa.agent;

import java.util.Optional;

import gr.aueb.java.archifactor.jpa.enums.RepairHarness;

public class RepairAgentFactory {

	public static Optional<RepairAgent> createAgent(RepairHarness harness, String model) {
		if (harness == RepairHarness.CLAUDE) {
			return Optional.of(new ClaudeRepairAgent(model));
		} else if (harness == RepairHarness.CODEX) {
			return Optional.of(new CodexRepairAgent(model));
		}
		return Optional.empty();
	}
}
