package gr.aueb.java.jpa;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.Annotation;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.SystemObject;

public class JpaModel {

	private Set<EntityObject> entities = new HashSet<EntityObject>();
	private Set<AssociationObject> associations = new HashSet<AssociationObject>();

	public void addEntity(EntityObject entity) {
		entities.add(entity);
	}

	public void addAssociation(AssociationObject assoc) {
		associations.add(assoc);
	}

	public void initialize(SystemObject systemObj) {

		entities.clear();

		Set<EntityObject> result = systemObj.getClassObjects().stream()
				.filter(c -> isEntity(c))
				.map(c -> new EntityObject(c))
				.collect(Collectors.toSet());

		entities.addAll(result);

	}

	public boolean hasJpaEntities(IPackageFragment fragment) {

//		Arrays.asList(fragment.getCompilationUnits()).stream()
//		.filter(cu -> cu.getJavaModel().get)
//		
		return false;
	}
	
	public Set<EntityObject> getEntities() {
		return entities;
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
}
