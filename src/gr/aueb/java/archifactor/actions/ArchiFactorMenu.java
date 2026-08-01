package gr.aueb.java.archifactor.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;

public class ArchiFactorMenu implements IWorkbenchWindowActionDelegate {
    private IWorkbenchWindow window;

    public ArchiFactorMenu() {
    }

    public void run(IAction action) {
        IWorkbenchPage page = window.getActivePage();
        try {
            if (action.getId().equals("gr.aueb.java.jdeodorant.actions.IdentifyCandidateModules")) {
                page.showView("gr.aueb.java.jdeodorant.views.IdentifyCandidateModules");
            } else if (action.getId().equals("gr.aueb.java.jdeodorant.actions.UnmapJpaRelationships")) {
                page.showView("gr.aueb.java.jdeodorant.views.UnmapJpaRelationships");
            } else if (action.getId().equals("gr.aueb.java.jdeodorant.actions.RepairJpqlQueries")) {
                page.showView("gr.aueb.java.jdeodorant.views.RepairJpqlQueries");
            }
        } catch (PartInitException e) {
            e.printStackTrace();
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
    }

    public void dispose() {
    }

    public void init(IWorkbenchWindow window) {
        this.window = window;
    }
}
