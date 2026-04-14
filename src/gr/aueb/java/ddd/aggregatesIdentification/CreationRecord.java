package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.Objects;

import gr.uom.java.ast.ClassObject;

public class CreationRecord {
    private ClassObject created;
    private ClassObject createdBy;
    
    public CreationRecord(ClassObject created, ClassObject createdBy) {
        this.created = created;
        this.createdBy = createdBy;
    }
    
    public ClassObject getCreated() {
        return created;
    }
    
    public void setCreated(ClassObject created) {
        this.created = created;
    }
    
    public ClassObject getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(ClassObject createdBy) {
        this.createdBy = createdBy;
    }
    
    @Override
	public int hashCode() {
		return Objects.hash(created, createdBy);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CreationRecord other = (CreationRecord) obj;
		return Objects.equals(created, other.created) && Objects.equals(createdBy, other.createdBy);
	}

	@Override
    public String toString() {
        return "CreationRecord [created=" + created.getName() + ", createdBy=" + createdBy.getName() + "]";
    }
}
