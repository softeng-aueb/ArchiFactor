package gr.aueb.java.archifactor.jpa.model;

import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;

public class RelationshipInfo {
    private String fromEntity;
    private JpaRelationshipType relationshipType;
    private String toEntity;
    private boolean isOwningSide;
    private String joinColumnName;
    private String fieldName;
    private String originPkType;
    private String referencedPkType;
    private String referencedPkName;
    private String joinTableName;
    private String joinTableJoinColumns;
    private String joinTableInverseJoinColumns;

    public RelationshipInfo(String fromEntity, JpaRelationshipType relationshipType, String toEntity, boolean isOwningSide,
                            String joinColumnName, String fieldName, String originPkType, String referencedPkType, String referencedPkName,
                            String joinTableName, String joinTableJoinColumns, String joinTableInverseJoinColumns) {
        this.fromEntity = fromEntity;
        this.relationshipType = relationshipType;
        this.toEntity = toEntity;
        this.isOwningSide = isOwningSide;
        this.joinColumnName = joinColumnName;
        this.fieldName = fieldName;
        this.originPkType = originPkType;
        this.referencedPkType = referencedPkType;
        this.referencedPkName = referencedPkName;
        this.joinTableName = joinTableName;
        this.joinTableJoinColumns = joinTableJoinColumns;
        this.joinTableInverseJoinColumns = joinTableInverseJoinColumns;
    }

    public String getFromEntity() {
        return fromEntity;
    }

    public JpaRelationshipType getRelationshipType() {
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

    public String getOriginPkType() {
        return originPkType;
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

    public String getJoinTableName() {
        return joinTableName;
    }

    public void setJoinTableName(String joinTableName) {
        this.joinTableName = joinTableName;
    }

    public String getJoinTableJoinColumns() {
        return joinTableJoinColumns;
    }

    public void setJoinTableJoinColumns(String joinTableJoinColumns) {
        this.joinTableJoinColumns = joinTableJoinColumns;
    }

    public String getJoinTableInverseJoinColumns() {
        return joinTableInverseJoinColumns;
    }

    public void setJoinTableInverseJoinColumns(String joinTableInverseJoinColumns) {
        this.joinTableInverseJoinColumns = joinTableInverseJoinColumns;
    }
}