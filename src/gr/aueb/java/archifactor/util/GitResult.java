package gr.aueb.java.archifactor.util;

public class GitResult {
    private final int exitCode;
    private final String output;

    GitResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public String getOutput() {
        return output;
    }
}
