package gr.aueb.java.ddd.aggregatesIdentification;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.swt.widgets.Text;

import gr.aueb.java.jpa.JpaModel;
import gr.aueb.java.jpa.EntityObject;

import java.util.List;

public class AggregationsIdentificationView extends ViewPart {
    public static final String ID = "gr.aueb.java.ddd.aggregatesIdentification.AggregationsIdentificationView";

    private Text text;

    @Override
    public void createPartControl(Composite parent) {
        text = new Text(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
        displayEntities();
    }

    private void displayEntities() {
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

        text.setText(entityNames.toString());
    }

    @Override
    public void setFocus() {
        text.setFocus();
    }
}
