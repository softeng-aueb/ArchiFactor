package gr.aueb.java.archifactor.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GitUtils {
    private static final long TIMEOUT_SECONDS = 60;

    public static boolean isGitAvailable() {
        return run(null, "--version").isSuccess();
    }

    public static boolean isGitRepository(File directory) {
        if (directory == null) {
            return false;
        }

        GitResult result = run(directory, "rev-parse", "--is-inside-work-tree");
        return result.isSuccess() && "true".equals(result.getOutput());
    }

    public static boolean hasUncommittedChanges(File directory) {
        if (directory == null) {
            return false;
        }

        GitResult result = run(directory, "status", "--porcelain");
        return result.isSuccess() && !result.getOutput().isEmpty();
    }

    public static GitResult commitAll(File directory, String message) {
        if (directory == null) {
            return new GitResult(-1, "No working directory provided for the git commit");
        }

        GitResult addResult = run(directory, "add", "-A");
        if (!addResult.isSuccess()) {
            return addResult;
        }
        return run(directory, "commit", "-m", message);
    }

    private static GitResult run(File workingDirectory, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory);
            }
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new GitResult(-1, "git command timed out: " + String.join(" ", command));
            }
            return new GitResult(process.exitValue(), output.toString().trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitResult(-1, "git command interrupted: " + String.join(" ", command));
        } catch (Exception e) {
            return new GitResult(-1, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
