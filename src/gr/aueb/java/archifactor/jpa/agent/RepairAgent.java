package gr.aueb.java.archifactor.jpa.agent;

import java.io.File;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Runs an autonomous coding agent that repairs the broken queries described in
 * the prompt, leaving its edits uncommitted in {@code workingDirectory}.
 *
 * This interface is the seam that isolates the agent harness from the rest of 
 * the plugin: callers supply a prompt and a turn budget, receive each line of 
 * the agent's output through {@code outputListener} as it is produced, and get 
 * a harness-agnostic {@link AgentOutcome} at the end. Supporting a different 
 * agent harness means adding another implementation and changing nothing else.
 */
public interface RepairAgent {
    AgentOutcome run(String prompt, int maxTurns, File workingDirectory, Consumer<String> outputListener, IProgressMonitor monitor);
}
