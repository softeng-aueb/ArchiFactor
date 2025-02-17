package gr.aueb.java.ddd.aggregatesIdentification;

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
    public String toString() {
        return "CreationRecord [created=" + created.getName() + ", createdBy=" + createdBy.getName() + "]";
    }
}
