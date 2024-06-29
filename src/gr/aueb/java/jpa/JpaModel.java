package gr.aueb.java.jpa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.Annotation;

import gr.uom.java.ast.ClassObject;

public class JpaModel {

	private Set<EntityObject> entities = new HashSet<EntityObject>();
	private Set<AssociationObject> associations = new HashSet<AssociationObject>();
	

	public void addEntity(EntityObject entity) {
		entities.add(entity);
	}
	
	public void addAssociation(AssociationObject assoc) {
		associations.add(assoc);
	}

	
	/**
	 * Update the model with a new EntityObject from the given ClassObj.
	 * If the ClassObj does not have an @Entity annotation then it is ignored
	 * @param classsObj
	 */
	public void update(ClassObject classsObj) {
		
	}
	
	public boolean hasJpaEntities(IPackageFragment fragment) {

//		Arrays.asList(fragment.getCompilationUnits()).stream()
//		.filter(cu -> cu.getJavaModel().get)
//		
		return false;
	}

	public static boolean isEntity(ClassObject classObj) {
		List<Annotation> annotations = classObj.getAnnotations();
		if (annotations.size() > 0) {
			for (Annotation ann : annotations) {
				if (ann.getTypeName().getFullyQualifiedName().equals("Entity")) {
					return true;
				}
			}
		}
		return false;
	}
	
    public List<EntityObject> getEntities() {
        return new ArrayList<EntityObject>(entities);
    }
}
