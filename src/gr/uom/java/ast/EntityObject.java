package gr.uom.java.ast;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class EntityObject {
	
	private ClassObject classObject;
	private Set<FieldObject> associatedObjects;
	private FieldObject idField; 
	
	
	public EntityObject(ClassObject classObject, Set<FieldObject> associatedObjects) {
		this.classObject = classObject;
		this.associatedObjects = associatedObjects;
	}
	
	
	public EntityObject(ClassObject classObject) {
		this.classObject = classObject;
		this.associatedObjects = new HashSet<FieldObject>();
	}



	public ClassObject getClassObject() {
		return classObject;
	}


	public void setClassObject(ClassObject classObject) {
		this.classObject = classObject;
	}


	public Set<FieldObject> getAssociatedObjects() {
		return associatedObjects;
	}


	public void addAssociatedObject(FieldObject associatedObject) {
		this.associatedObjects.add(associatedObject);
	}
	
	public void removeAssociatedObject(FieldObject associatedObject) {
		this.associatedObjects.remove(associatedObject);

	}
	
	
	public FieldObject getIdField() {
		return idField;
	}


	public void setIdField(FieldObject idField) {
		this.idField = idField;
	}


	public FieldObject getAssociatedObjectByClass(ClassObject classObject) {
		for(FieldObject fieldObject:this.associatedObjects) {
			if(fieldObject.getType().getClassType().equals(classObject.getName())) {
				return fieldObject;
			}else if(fieldObject.getType().getGenericType()!=null){
				if(fieldObject.getType().getGenericType().equals("<"+classObject.getClassObject().getName()+">")) {
					return fieldObject;
				}
				
			}
		}
		return null;
	}
	
	public Annotation getAssociatedObjectAnnotationByField(FieldObject field) {
		for(FieldObject fieldObject:this.associatedObjects) {
			if(fieldObject.equals(field)) {
				for(Annotation ann:fieldObject.getAnnotations()) {
					String name = ann.getTypeName().getFullyQualifiedName();
					if((name.equals("OneToMany")||(name.equals("ManyToOne"))||(name.equals("OneToOne"))||(name.equals("ManyToMany")))) {
						return ann;
					}
				}
			}
		}
		return null;
	}
	
	public Set<MethodDeclaration> getMethodDeclarationsByField(FieldObject field){
		Set<MethodDeclaration> methodDeclarations = new LinkedHashSet<MethodDeclaration>();
		for(MethodObject method:this.classObject.getMethodList()) {
			for(FieldObject fieldObject:this.getClassObject().getFieldsAccessedInsideMethod(method)) {
				if(field.equals(fieldObject)) {
					methodDeclarations.add(method.getMethodDeclaration());
				}
			}
			for(String parameter:method.getParameterList()) {
				if(field.getType().toString().equals(parameter)) {
					methodDeclarations.add(method.getMethodDeclaration());
				}else if(field.getType().getGenericType()!=null){
					if(field.getType().getGenericType().equals("<"+parameter+">")) {
						methodDeclarations.add(method.getMethodDeclaration());
					}
				}
			}
			
		}
		return methodDeclarations;
	}

	
	
	
	
	
	
	

}
