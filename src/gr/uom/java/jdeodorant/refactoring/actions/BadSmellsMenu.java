package gr.uom.java.jdeodorant.refactoring.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;

public class BadSmellsMenu implements IWorkbenchWindowActionDelegate {
    private IWorkbenchWindow window;

    public BadSmellsMenu() {
    }

    public void run(IAction action) {
        IWorkbenchPage page = window.getActivePage();
        try {
            if (action.getId().equals("gr.uom.java.jdeodorant.actions.FeatureEnvy")) {
                page.showView("gr.uom.java.jdeodorant.views.FeatureEnvy");
            } else if (action.getId().equals("gr.uom.java.jdeodorant.actions.TypeChecking")) {
                page.showView("gr.uom.java.jdeodorant.views.TypeChecking");
            } else if (action.getId().equals("gr.uom.java.jdeodorant.actions.LongMethod")) {
                page.showView("gr.uom.java.jdeodorant.views.LongMethod");
            } else if (action.getId().equals("gr.uom.java.jdeodorant.actions.GodClass")) {
                page.showView("gr.uom.java.jdeodorant.views.GodClass");
            } else if (action.getId().equals("gr.uom.java.jdeodorant.actions.DuplicatedCode")) {
                page.showView("gr.uom.java.jdeodorant.views.DuplicatedCode");
            } else if (action.getId().equals("gr.uom.java.jdeodorant.actions.MicroserviceExtraction")) {
                page.showView("gr.uom.java.jdeodorant.views.MicroserviceExtraction");
            } else if (action.getId().equals("gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentification")) {
                page.showView("gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView");
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
