package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import gr.aueb.java.jpa.JpaModel;
import gr.aueb.java.jpa.EntityObject;

import java.util.List;

public class AggregationsIdentificationHandler extends AbstractHandler {
    public Object execute(ExecutionEvent event) throws ExecutionException {
        JpaModel jpaModel = new JpaModel();
        List<EntityObject> entities = jpaModel.getEntities();
        StringBuilder entityNames = new StringBuilder();

        if (entities.isEmpty()) {
            entityNames.append("No entities found.");
        } else {
            for (EntityObject entity : entities) {
                if (entityNames.length() > 0) {
                    entityNames.append("\n");
                }
                entityNames.append(entity.getClassObject().getName());
            }
        }

        MessageDialog.openInformation(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Aggregations Identification",
                "List of entities:\n" + entityNames.toString());
        return null;
    }
}
