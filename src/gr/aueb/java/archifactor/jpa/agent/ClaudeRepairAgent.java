package gr.aueb.java.archifactor.jpa.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * {@link RepairAgent} backed by the Claude Code CLI ({@code claude -p}).
 *
 * Everything Claude-specific is confined to this class: the executable, the flags
 * that make the run non-interactive and bounded, the permission rules, and the
 * streaming output format. The rest of the plugin sees only {@link RepairAgent}
 * and {@link AgentOutcome}.
 */
public class ClaudeRepairAgent implements RepairAgent {
    private static final long POLL_SECONDS = 2;

    // Bash is allowed broadly so the agent can run whatever command the project
    // uses for its tests; file edits are auto-accepted via --permission-mode acceptEdits.
    // Monitor lets it block on a long test run inside one turn instead of burning a
    // turn per one-off poll of the log.
    private static final String ALLOWED_TOOLS = "Read,Grep,Glob,Edit,Write,Bash,Monitor";

    // Deny rules take precedence over the allowlist: never let the agent change the
    // git history or reach the network.
    private static final String DISALLOWED_TOOLS =
        "WebFetch,WebSearch,"
        + "Bash(git commit:*),Bash(git reset:*),Bash(git checkout:*),"
        + "Bash(git rebase:*),Bash(git stash:*),Bash(git push:*)";

    // A full test suite can take ~15 minutes inside a single Bash call; the CLI's default
    // 2-minute limit would force the agent to poll, burning a turn every few seconds.
    private static final String BASH_TIMEOUT_MS = String.valueOf(TimeUnit.MINUTES.toMillis(30));

    private final String model;

    public ClaudeRepairAgent(String model) {
        this.model = model;
    }

    /**
     * Quick preflight: whether the {@code claude} CLI can be launched at all.
     */
    @Override
    public boolean isAvailable() {
        try {
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("cmd");
                command.add("/c");
            }
            command.add("claude");
            command.add("--version");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = processBuilder.start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AgentOutcome run(String prompt, int maxTurns, File workingDirectory, Consumer<String> outputListener, IProgressMonitor monitor) {
        Process process;
        try {
            process = startProcess(maxTurns, workingDirectory);
        } catch (Exception e) {
            emit(outputListener, "Failed to start the agent: " + e.getMessage());
            return new AgentOutcome(false, "");
        }

        StringBuilder stdout = new StringBuilder();
        Thread outReader = drain(process.getInputStream(), stdout, outputListener);
        Thread errReader = drain(process.getErrorStream(), null, outputListener);
        writePromptToStdin(process, prompt);

        boolean cancelled = awaitOrCancel(process, monitor);
        join(outReader);
        join(errReader);

        if (cancelled) {
            return new AgentOutcome(false, stdout.toString());
        }
        return new AgentOutcome(process.exitValue() == 0, stdout.toString());
    }

    private Process startProcess(int maxTurns, File workingDirectory) throws Exception {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd");
            command.add("/c");
        }
        command.add("claude");
        command.add("-p");
        command.add("--output-format");
        command.add("stream-json"); // one JSON event per line, so progress is visible live
        command.add("--verbose");
        command.add("--model");
        command.add(model);
        command.add("--max-turns");
        command.add(String.valueOf(maxTurns));
        command.add("--permission-mode");
        command.add("acceptEdits");
        command.add("--allowedTools");
        command.add(ALLOWED_TOOLS);
        command.add("--disallowedTools");
        command.add(DISALLOWED_TOOLS);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory);
        processBuilder.environment().put("BASH_DEFAULT_TIMEOUT_MS", BASH_TIMEOUT_MS);
        processBuilder.environment().put("BASH_MAX_TIMEOUT_MS", BASH_TIMEOUT_MS);
        return processBuilder.start();
    }

    private void writePromptToStdin(Process process, String prompt) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // The process may have already exited; its output and exit code will explain why.
        }
    }

    private Thread drain(InputStream stream, StringBuilder sink, Consumer<String> listener) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sink != null) {
                            sink.append(line).append('\n');
                        }
                        emit(listener, line);
                    }
                } catch (Exception e) {
                    // Stream closes when the process ends; nothing useful to add.
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Waits for the process, polling so the user can cancel. On cancellation the
     * whole process tree is destroyed: the launched process spawns children (on
     * Windows "cmd /c" wraps the CLI, which in turn runs the agent's commands), so
     * destroying only the top process would leave them running.
     */
    private boolean awaitOrCancel(Process process, IProgressMonitor monitor) {
        try {
            while (!process.waitFor(POLL_SECONDS, TimeUnit.SECONDS)) {
                if (monitor != null && monitor.isCanceled()) {
                    killTree(process);
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(process);
            return true;
        }
    }

    private void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void join(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(POLL_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void emit(Consumer<String> listener, String line) {
        if (listener != null) {
            listener.accept(line);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
