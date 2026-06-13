package gr.aueb.java.archifactor.jpa.agent;

/**
 * Harness-agnostic result of an agent run: whether it finished cleanly, and the
 * final report the agent produced (its own output, in the shape the prompt asks for).
 */
public class AgentOutcome {
    private final boolean success;
    private final String report;

    public AgentOutcome(boolean success, String report) {
        this.success = success;
        this.report = report;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReport() {
        return report;
    }
}
