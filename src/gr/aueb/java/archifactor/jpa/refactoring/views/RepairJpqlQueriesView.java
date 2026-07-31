package gr.aueb.java.archifactor.jpa.refactoring.views;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.*;
import org.eclipse.ui.part.*;
import org.eclipse.ui.progress.IProgressService;

import gr.aueb.java.archifactor.jpa.agent.AgentPromptBuilder;
import gr.aueb.java.archifactor.jpa.agent.JpqlRepairAgentRunner;
import gr.aueb.java.archifactor.jpa.agent.RepairAgent;
import gr.aueb.java.archifactor.jpa.agent.RepairAgentFactory;
import gr.aueb.java.archifactor.jpa.enums.RepairHarness;
import gr.aueb.java.archifactor.jpa.manifest.UnmapManifestGenerator;
import gr.aueb.java.archifactor.util.GitUtils;
import gr.uom.java.jdeodorant.refactoring.views.ElementChangedListener;

/**
 * Standalone view for the AI JQPL object-model queries repair phase. Depends on 
 * the "Unmap JPA Relationships" refactoring having written the manifest and committed.
 */
public class RepairJpqlQueriesView extends ViewPart {
    private static final int AGENT_MAX_TURNS = 100;
    private static final String MODEL_PATTERN = "[A-Za-z0-9._/\\[\\]-]+";

    private ComboViewer projectComboViewer;
    private IJavaProject selectedProject;
    private ComboViewer agentComboViewer;
    private RepairHarness selectedHarness = RepairHarness.NONE;
    private Text modelText;
    private Text instructionsText;

    @Override
    public void createPartControl(Composite parent) {
        // Grid layout:
        // 1) Row 1 | Column 1: Label ("Select project:")
        // 2) Row 1 | Column 2: ComboViewer (dropdown)
        // 3) Row 2 | Column 1: Label ("Select agent:")
        // 4) Row 2 | Column 2: ComboViewer (dropdown)
        // 5) Row 3 | Column 1: Label ("Model:")
        // 6) Row 3 | Column 2: Text (model field)
        // 7) Row 4 | Column 1: Label ("Additional instructions:")
        // 8) Row 4 | Column 2: Text (multi-line instructions field)
        // 9) Row 5 | Columns 1+2: Run button
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 10;
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        container.setLayout(layout);

        // Project selection dropdown
        Label projectLabel = new Label(container, SWT.NONE);
        projectLabel.setText("Select project:");

        projectComboViewer = new ComboViewer(container, SWT.READ_ONLY);
        projectComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectComboViewer.setContentProvider(ArrayContentProvider.getInstance());
        projectComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((IJavaProject) element).getElementName();
            }
        });

        populateProjectCombo();
        if (!projectComboViewer.getSelection().isEmpty()) {
            selectedProject = (IJavaProject) ((IStructuredSelection) projectComboViewer.getSelection()).getFirstElement();
        }
        
        projectComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                selectedProject = selection.isEmpty() ? null : (IJavaProject) selection.getFirstElement();
            }
        });

        // Agent selection dropdown
        Label agentLabel = new Label(container, SWT.NONE);
        agentLabel.setText("Select agent:");

        agentComboViewer = new ComboViewer(container, SWT.READ_ONLY);
        agentComboViewer.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        agentComboViewer.setContentProvider(ArrayContentProvider.getInstance());
        agentComboViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((RepairHarness) element).getDisplayName();
            }
        });
        agentComboViewer.setInput(RepairHarness.values());
        agentComboViewer.setSelection(new StructuredSelection(RepairHarness.NONE));

        // Model field
        Label modelLabel = new Label(container, SWT.NONE);
        modelLabel.setText("Model:");

        modelText = new Text(container, SWT.BORDER | SWT.SINGLE);
        modelText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        modelText.setToolTipText("The model the agent should use (for example \"opus\" or \"gpt-5.5\").");
        modelText.setEnabled(false);

        // Additional instructions field
        Label instructionsLabel = new Label(container, SWT.NONE);
        instructionsLabel.setText("Additional instructions:");
        instructionsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        instructionsText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData instructionsGridData = new GridData(SWT.FILL, SWT.FILL, true, false);
        instructionsGridData.heightHint = 80;
        instructionsText.setLayoutData(instructionsGridData);
        instructionsText.setToolTipText("Optional project-specific guidance passed to the agent verbatim "
            + "(for example, how to run this project's test suite).");
        instructionsText.setEnabled(false);

        // Enables the Model field
        agentComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                onAgentSelected();
            }
        });

        // Run button
        Button runButton = new Button(container, SWT.PUSH);
        runButton.setText("Repair JPQL Queries");
        GridData buttonGridData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        runButton.setLayoutData(buttonGridData);
        runButton.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                runRepair();
            }
        });
        
        JavaCore.addElementChangedListener(ElementChangedListener.getInstance());
    }

    private void onAgentSelected() {
        IStructuredSelection selection = (IStructuredSelection) agentComboViewer.getSelection();
        RepairHarness harness = (RepairHarness) selection.getFirstElement();
        if (harness == RepairHarness.NONE) {
            selectedHarness = RepairHarness.NONE;
            modelText.setText("");
            modelText.setEnabled(false);
            instructionsText.setText("");
            instructionsText.setEnabled(false);
            return;
        }

        if (!isHarnessAvailable(harness)) {
            MessageDialog.openWarning(getSite().getShell(), "Agent Not Available",
                "Could not run the " + harness.getDisplayName() + " CLI. Make sure it is installed, "
                + "authenticated, and on the PATH visible to Eclipse, then select it again.");
            // Reset to None so the user can fix the setup and re-select.
            agentComboViewer.setSelection(new StructuredSelection(RepairHarness.NONE));
            return;
        }

        selectedHarness = harness;
        modelText.setEnabled(true);
        instructionsText.setEnabled(true);
    }

    private boolean isHarnessAvailable(final RepairHarness harness) {
        final boolean[] available = { false };
        try {
            IWorkbench wb = PlatformUI.getWorkbench();
            IProgressService ps = wb.getProgressService();
            ps.busyCursorWhile(new IRunnableWithProgress() {
                public void run(IProgressMonitor monitor) {
                    available[0] = RepairAgentFactory.createAgent(harness, null).map(RepairAgent::isAvailable).orElse(false);
                }
            });
        } catch (Exception e) {
            return false;
        }
        return available[0];
    }

    private void runRepair() {
        Shell shell = getSite().getShell();

        if (selectedProject == null) {
            MessageDialog.openError(shell, "No Project Selected", "Select a project with broken JPQL object-model queries to repair.");
            return;
        }

        if (selectedHarness == RepairHarness.NONE) {
            MessageDialog.openInformation(shell, "No Agent Selected", "Select an agent to run. \"None\" leaves the AI repair step off.");
            return;
        }

        String model = modelText.getText().trim();
        if (model.isEmpty()) {
            MessageDialog.openError(shell, "No Model", "Enter the model the agent should use.");
            return;
        }
        if (!model.matches(MODEL_PATTERN)) {
            MessageDialog.openError(shell, "Invalid Model",  "The model name contains unexpected characters. Use letters, digits, and . _ - / [ ] only.");
            return;
        }

        File projectDirectory = getProjectDirectory();
        if (projectDirectory == null) {
            MessageDialog.openError(shell, "Project Location Unavailable", "Could not resolve the project's location on disk.");
            return;
        }

        File manifestFile = new File(projectDirectory, UnmapManifestGenerator.MANIFEST_FILE_NAME);
        if (!manifestFile.isFile()) {
            MessageDialog.openError(shell, "No Manifest Found",
                "No " + UnmapManifestGenerator.MANIFEST_FILE_NAME + " was found in the project root.\n\n"
                + "Run \"Unmap JPA Relationships\" first; it writes and commits the manifest that this step repairs against.");
            return;
        }

        String manifestJson;
        try {
            manifestJson = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MessageDialog.openError(shell, "Manifest Unreadable", "Could not read " + UnmapManifestGenerator.MANIFEST_FILE_NAME + ": " + e.getMessage());
            return;
        }

        String baselineCommit = GitUtils.getCommitByMessage(projectDirectory, UnmapJpaRelationships.DETERMINISTIC_COMMIT_MESSAGE);
        if (baselineCommit == null) {
            MessageDialog.openError(shell, "Baseline Commit Not Found",
                "Could not fine the baseline commit. The AI repair step expects the deterministic "
                + "\"Unmap JPA Relationships\" phase to have been committed in this git repository.");
            return;
        }

        String prompt;
        try {
            prompt = AgentPromptBuilder.build(manifestJson, baselineCommit, instructionsText.getText());
        } catch (IOException e) {
            MessageDialog.openError(shell, "Agent Prompt Error", "Could not build the agent prompt: " + e.getMessage());
            return;
        }

        RepairAgentFactory.createAgent(selectedHarness, model).ifPresent(agent ->
            new JpqlRepairAgentRunner(selectedProject.getProject(), projectDirectory, prompt, AGENT_MAX_TURNS, agent).schedule());
    }

    private void populateProjectCombo() {
        try {
            List<IJavaProject> projects = new ArrayList<IJavaProject>();
            for (IJavaProject project : JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects()) {
                projects.add(project);
            }

            projectComboViewer.setInput(projects);
            if (!projects.isEmpty()) {
                projectComboViewer.getCombo().select(0);
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
    }

    private File getProjectDirectory() {
        IPath location = selectedProject.getProject().getLocation();
        return location != null ? location.toFile() : null;
    }

    @Override
    public void setFocus() {
        projectComboViewer.getControl().setFocus();
    }
}
