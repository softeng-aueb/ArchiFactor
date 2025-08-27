package gr.aueb.java.archifactor.refactoring.manipulators;

public class RelationshipInfo {
    private String fromEntity;
    private String relationshipType;
    private String toEntity;
    private boolean isOwningSide;
    private String joinColumnName;
    private String fieldName;
    private String referencedPkType;
    private String referencedPkName;

    public RelationshipInfo(String fromEntity, String relationshipType, String toEntity, boolean isOwningSide,
                            String joinColumnName, String fieldName, String referencedPkType, String referencedPkName) {
        this.fromEntity = fromEntity;
        this.relationshipType = relationshipType;
        this.toEntity = toEntity;
        this.isOwningSide = isOwningSide;
        this.joinColumnName = joinColumnName;
        this.fieldName = fieldName;
        this.referencedPkType = referencedPkType;
        this.referencedPkName = referencedPkName;
    }
    
    public String getFromEntity() {
        return fromEntity;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public String getToEntity() {
        return toEntity;
    }
    
    public boolean isOwningSide() {
        return isOwningSide;
    }
    
    public String getJoinColumnName() {
        return joinColumnName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public String getReferencedPkType() {
        return referencedPkType;
    }
    
    public String getReferencedPkName() {
        return referencedPkName;
    }
    
    public void setJoinColumnName(String joinColumnName) {
        this.joinColumnName = joinColumnName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public void setReferencedPkType(String referencedPkType) {
        this.referencedPkType = referencedPkType;
    }
    
    public void setReferencedPkName(String referencedPkName) {
        this.referencedPkName = referencedPkName;
    }
}