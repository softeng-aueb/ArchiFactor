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
 * {@link RepairAgent} backed by the OpenAI Codex CLI ({@code codex exec}).
 *
 * Everything Codex-specific is confined to this class}: the executable, the flags 
 * that make the run non-interactive, the sandbox/permission policy, and where the 
 * report and the live transcript come out. The rest of the plugin sees only {@link RepairAgent}
 * and {@link AgentOutcome}.
 */
public class CodexRepairAgent implements RepairAgent {
    private static final String MODEL = "gpt-5.5";
    private static final long POLL_SECONDS = 2;

    /**
     * Quick preflight: whether the {@code codex} CLI can be launched at all.
     */
    public static boolean isAvailable() {
        try {
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("cmd");
                command.add("/c");
            }
            command.add("codex");
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
            process = startProcess(workingDirectory);
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

    private Process startProcess(File workingDirectory) throws Exception {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd");
            command.add("/c");
        }
        command.add("codex");
        command.add("exec");
        command.add("--model");
        command.add(MODEL);
        command.add("--sandbox");
        command.add("workspace-write");
        command.add("-c");
        command.add("sandbox_workspace_write.network_access=true");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory);
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
     * whole process tree is destroyed: "cmd /c codex" spawns node, so destroying
     * only the launched process would leave node running.
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
