package gr.aueb.java.archifactor.jpa.agent;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Runs the JPQL-repair agent as a background job: streams the agent's live
 * output to an Eclipse console, refreshes the workspace so Eclipse sees the
 * edits, and reports the outcome.
 */
public class JpqlRepairAgentRunner extends Job {
    private static final String CONSOLE_NAME = "ArchiFactor JQPL Repair Agent";

    private final IProject project;
    private final File workingDirectory;
    private final String prompt;
    private final int maxTurns;
    private final RepairAgent agent;

    public JpqlRepairAgentRunner(IProject project, File workingDirectory, String prompt, int maxTurns, RepairAgent agent) {
        super("Repairing broken JPQL queries");
        this.project = project;
        this.workingDirectory = workingDirectory;
        this.prompt = prompt;
        this.maxTurns = maxTurns;
        this.agent = agent;
        setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Running the repair agent (watch the 'ArchiFactor JQPL Repair Agent' console)...", IProgressMonitor.UNKNOWN);

        MessageConsole console = getConsole();
        revealConsole(console);
        MessageConsoleStream stream = console.newMessageStream();

        AgentOutcome outcome;
        try {
            outcome = agent.run(prompt, maxTurns, workingDirectory, line -> stream.println(line), monitor);
        } finally {
            close(stream);
        }

        refreshProject(monitor);
        monitor.done();

        if (monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
        }

        showReport(outcome);
        return Status.OK_STATUS;
    }

    private MessageConsole getConsole() {
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        for (IConsole existing : manager.getConsoles()) {
            if (existing instanceof MessageConsole && CONSOLE_NAME.equals(existing.getName())) {
                MessageConsole console = (MessageConsole) existing;
                console.clearConsole();
                return console;
            }
        }

        MessageConsole console = new MessageConsole(CONSOLE_NAME, null);
        manager.addConsoles(new IConsole[] { console });
        return console;
    }

    private void revealConsole(MessageConsole console) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
            }
        });
    }

    private void refreshProject(IProgressMonitor monitor) {
        try {
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        } catch (Exception e) {
            // A failed refresh only means the user must refresh manually; not fatal.
        }
    }

    private void showReport(AgentOutcome outcome) {
        final boolean green = outcome.isSuccess() && outcome.getReport().contains("RESULT: SUCCESS");
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                Shell shell = Display.getDefault().getActiveShell();
                if (green) {
                    MessageDialog.openInformation(shell, "JPQL Repair Agent",
                        "The agent reports the test suite is green. Review the uncommitted "
                        + "changes; the full transcript is in the 'ArchiFactor JQPL Repair Agent' console.");
                } else {
                    MessageDialog.openWarning(shell, "JPQL Repair Agent",
                        "The agent finished, but the test suite may not be fully green. Review the uncommitted "
                        + "changes and the 'ArchiFactor JQPL Repair Agent' console before keeping them.");
                }
            }
        });
    }

    private void close(MessageConsoleStream stream) {
        try {
            stream.close();
        } catch (Exception e) {
            // Best effort.
        }
    }
}
