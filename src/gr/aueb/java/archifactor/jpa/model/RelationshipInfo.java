package gr.aueb.java.archifactor.jpa.model;

import java.util.Collections;
import java.util.Set;

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
    private Set<String> cascadeTypes;
    private boolean orphanRemoval;

    private RelationshipInfo(Builder builder) {
        this.fromEntity = builder.fromEntity;
        this.relationshipType = builder.relationshipType;
        this.toEntity = builder.toEntity;
        this.isOwningSide = builder.isOwningSide;
        this.joinColumnName = builder.joinColumnName;
        this.fieldName = builder.fieldName;
        this.originPkType = builder.originPkType;
        this.referencedPkType = builder.referencedPkType;
        this.referencedPkName = builder.referencedPkName;
        this.joinTableName = builder.joinTableName;
        this.joinTableJoinColumns = builder.joinTableJoinColumns;
        this.joinTableInverseJoinColumns = builder.joinTableInverseJoinColumns;
        this.cascadeTypes = builder.cascadeTypes;
        this.orphanRemoval = builder.orphanRemoval;
    }

    public static Builder builder() {
        return new Builder();
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

    public Set<String> getCascadeTypes() {
        return cascadeTypes;
    }

    public boolean isOrphanRemoval() {
        return orphanRemoval;
    }

    public boolean hasDroppedSemantics() {
        return !cascadeTypes.isEmpty() || orphanRemoval;
    }

    public static class Builder {
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
        private Set<String> cascadeTypes = Collections.emptySet();
        private boolean orphanRemoval;

        private Builder() {
        }

        public Builder fromEntity(String fromEntity) {
            this.fromEntity = fromEntity;
            return this;
        }

        public Builder relationshipType(JpaRelationshipType relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public Builder toEntity(String toEntity) {
            this.toEntity = toEntity;
            return this;
        }

        public Builder isOwningSide(boolean isOwningSide) {
            this.isOwningSide = isOwningSide;
            return this;
        }

        public Builder joinColumnName(String joinColumnName) {
            this.joinColumnName = joinColumnName;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder originPkType(String originPkType) {
            this.originPkType = originPkType;
            return this;
        }

        public Builder referencedPkType(String referencedPkType) {
            this.referencedPkType = referencedPkType;
            return this;
        }

        public Builder referencedPkName(String referencedPkName) {
            this.referencedPkName = referencedPkName;
            return this;
        }

        public Builder joinTableName(String joinTableName) {
            this.joinTableName = joinTableName;
            return this;
        }

        public Builder joinTableJoinColumns(String joinTableJoinColumns) {
            this.joinTableJoinColumns = joinTableJoinColumns;
            return this;
        }

        public Builder joinTableInverseJoinColumns(String joinTableInverseJoinColumns) {
            this.joinTableInverseJoinColumns = joinTableInverseJoinColumns;
            return this;
        }

        public Builder cascadeTypes(Set<String> cascadeTypes) {
            this.cascadeTypes = cascadeTypes;
            return this;
        }

        public Builder orphanRemoval(boolean orphanRemoval) {
            this.orphanRemoval = orphanRemoval;
            return this;
        }

        public RelationshipInfo build() {
            return new RelationshipInfo(this);
        }
    }
}