package gr.aueb.java.jpa;

public class AssociationObject {

	String type;
	EntityObject ownerClass;
	EntityObject ownedClass;
	//FieldObject fieldObject;
	boolean isBidirectional;
	
	
	public AssociationObject(String type, EntityObject ownerClass, EntityObject nonOwnerClass, boolean isBidirectional) {
		this.type = type;
		this.ownerClass = ownerClass;
		this.ownedClass = nonOwnerClass;
		this.isBidirectional = isBidirectional;
	}


	public String getType() {
		return type;
	}


	public void setType(String type) {
		this.type = type;
	}


	public EntityObject getOwnerClass() {
		return ownerClass;
	}


	public void setOwnerClass(EntityObject ownerClass) {
		this.ownerClass = ownerClass;
	}


	public EntityObject getOwnedClass() {
		return ownedClass;
	}


	public void setOwnedClass(EntityObject ownedClass) {
		this.ownedClass = ownedClass;
	}


	public boolean isBidirectional() {
		return isBidirectional;
	}


	public void setBidirectional(boolean isBidirectional) {
		this.isBidirectional = isBidirectional;
	}
	
	
	


}
	

