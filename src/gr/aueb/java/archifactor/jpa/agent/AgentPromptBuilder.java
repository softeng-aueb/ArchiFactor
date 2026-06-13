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

    public static String build(String manifestJson, String baselineCommit) throws IOException {
        return loadTemplate()
            .replace("{{BASELINE_COMMIT}}", baselineCommit)
            .replace("{{MANIFEST}}", manifestJson);
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
