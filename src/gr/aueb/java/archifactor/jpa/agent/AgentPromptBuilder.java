package gr.aueb.java.archifactor.jpa.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class AgentPromptBuilder {
    private static final String TEMPLATE_PATH = "resources/jpql-repair-agent-prompt.md";

    public static String build(String manifestJson, String baselineCommit, String operatorInstructions) throws IOException {
        return loadTemplate()
            .replace("{{BASELINE_COMMIT}}", baselineCommit)
            .replace("{{OPERATOR_INSTRUCTIONS_SECTION}}", operatorInstructionsSection(operatorInstructions))
            .replace("{{MANIFEST}}", manifestJson);
    }

    private static String operatorInstructionsSection(String operatorInstructions) {
        if (operatorInstructions == null || operatorInstructions.trim().isEmpty()) {
            return "";
        }

        return "## Operator instructions\n\n"
            + "Project-specific guidance that takes precedence over the generic discovery advice above, but not over the hard constraints:\n"
            + operatorInstructions.trim();
    }

    private static String loadTemplate() throws IOException {
        Bundle bundle = FrameworkUtil.getBundle(AgentPromptBuilder.class);
        InputStream in = FileLocator.openStream(bundle, new Path(TEMPLATE_PATH), false);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
