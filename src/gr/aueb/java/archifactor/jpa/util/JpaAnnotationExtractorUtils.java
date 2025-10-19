package gr.aueb.java.archifactor.jpa.util;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ArrayInitializer;

import gr.aueb.java.archifactor.jpa.enums.JpaJoinType;
import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.exceptions.CompositeKeyException;
import gr.aueb.java.archifactor.jpa.model.JoinTableInfo;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.SystemObject;

import java.util.Iterator;
import java.util.ListIterator;

public class JpaAnnotationExtractorUtils {
    private SystemObject systemObject;
    
    public JpaAnnotationExtractorUtils(SystemObject systemObject) {
        this.systemObject = systemObject;
    }

    public String extractJoinColumnName(FieldObject field) {
        // First check if this field has a direct @JoinColumn annotation
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (JpaJoinType.fromAnnotationName(annotationType) == JpaJoinType.JOIN_COLUMN) {
                String columnName = extractAnnotationStringProperty(annotation, "name");
                if (columnName != null) {
                    return columnName;
                }
            }
        }
        
        // Check if this is a mappedBy relationship (@OneToOne(mappedBy="...") or @OneToMany(mappedBy="..."))
        String mappedByFieldName = extractMappedByProperty(field);
        if (mappedByFieldName != null) {
            // Find the referenced field in the target entity and get its @JoinColumn
            String targetEntityType = field.getType().getGenericType();
            if (targetEntityType != null) {
                targetEntityType = targetEntityType.replaceAll("[<>]", "").trim();
            } else {
                targetEntityType = field.getType().getClassType();
            }
            
            ClassObject targetEntity = findEntityByTypeName(targetEntityType);
            if (targetEntity != null) {
                FieldObject referencedField = findFieldInEntity(targetEntity, mappedByFieldName);
                if (referencedField != null) {
                    String joinColumnName = extractDirectJoinColumnName(referencedField);
                    if (joinColumnName != null) {
                        return joinColumnName;
                    }
                }
            }
        }
        return null;
    }

    private String extractMappedByProperty(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            JpaRelationshipType relType = JpaRelationshipType.fromAnnotationName(annotationType);
            if (relType == JpaRelationshipType.ONE_TO_MANY || relType == JpaRelationshipType.ONE_TO_ONE) {
                String mappedBy = extractAnnotationStringProperty(annotation, "mappedBy");
                if (mappedBy != null) {
                    return mappedBy;
                }
            }
        }
        return null;
    }

    private ClassObject findEntityByTypeName(String typeName) {
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            String className = classObj.getName();
            if (className.equals(typeName) || className.endsWith("." + typeName)) {
                return classObj;
            }
        }
        return null;
    }

    private FieldObject findFieldInEntity(ClassObject entity, String fieldName) {
        Iterator<FieldObject> fieldIterator = entity.getFieldIterator();
        while (fieldIterator.hasNext()) {
            FieldObject field = fieldIterator.next();
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private String extractDirectJoinColumnName(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (JpaJoinType.fromAnnotationName(annotationType) == JpaJoinType.JOIN_COLUMN) {
                String columnName = extractAnnotationStringProperty(annotation, "name");
                if (columnName != null) {
                    return columnName;
                }
            }
        }
        return null;
    }

    public String extractIdFieldType(String entityName) {
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (entityName.equals(classObj.getName())) {
                Iterator<FieldObject> fieldIterator = classObj.getFieldIterator();
                while (fieldIterator.hasNext()) {
                    FieldObject field = fieldIterator.next();
                    if (hasIdAnnotation(field)) {
                        return field.getType().getClassType();
                    }
                }
            }
        }
        return null;
    }

    public String extractIdFieldName(String entityName) {
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (entityName.equals(classObj.getName())) {
                Iterator<FieldObject> fieldIterator = classObj.getFieldIterator();
                while (fieldIterator.hasNext()) {
                    FieldObject field = fieldIterator.next();
                    if (hasIdAnnotation(field)) {
                        return field.getName();
                    }
                }
            }
        }
        return null;
    }

    private boolean hasIdAnnotation(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (annotationType.equals("Id")) {
                return true;
            }
        }
        return false;
    }

    private String extractAnnotationStringProperty(Annotation annotation, String propertyName) {
        if (annotation instanceof SingleMemberAnnotation) {
            SingleMemberAnnotation singleMember = (SingleMemberAnnotation) annotation;
            if (propertyName.equals("value")) {
                Expression value = singleMember.getValue();
                if (value instanceof StringLiteral) {
                    return ((StringLiteral) value).getLiteralValue();
                }
            }
        } else if (annotation instanceof NormalAnnotation) {
            NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
            for (Object obj : normalAnnotation.values()) {
                MemberValuePair pair = (MemberValuePair) obj;
                String pairName = pair.getName().getIdentifier();
                if (propertyName.equals(pairName)) {
                    Expression value = pair.getValue();
                    if (value instanceof StringLiteral) {
                        return ((StringLiteral) value).getLiteralValue();
                    }
                }
            }
        }
        return null;
    }

    public JoinTableInfo extractJoinTableInfo(FieldObject field) {
        // First check if this field has a direct @JoinTable annotation
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (JpaJoinType.fromAnnotationName(annotationType) == JpaJoinType.JOIN_TABLE) {
                String tableName = extractAnnotationStringProperty(annotation, "name");
                String joinColumns = extractJoinColumns(annotation, "joinColumns");
                String inverseJoinColumns = extractJoinColumns(annotation, "inverseJoinColumns");
                return new JoinTableInfo(tableName, joinColumns, inverseJoinColumns);
            }
        }

        // If there is no direct @JoinTable, check if this is a mappedBy relationship
        String mappedByFieldName = extractManyToManyMappedByProperty(field);
        if (mappedByFieldName == null) return null;

        String targetEntityType = field.getType().getGenericType();
        if (targetEntityType != null) {
        	targetEntityType = targetEntityType.replaceAll("[<>]", "").trim();
        }

        ClassObject targetEntity = findEntityByTypeName(targetEntityType);
        if (targetEntity == null) return null;

        FieldObject referencedField = findFieldInEntity(targetEntity, mappedByFieldName);
        if (referencedField == null) return null;

        JoinTableInfo owningInfo = extractJoinTableInfo(referencedField);
        if (owningInfo == null) return null;

        return new JoinTableInfo(owningInfo.getTableName(), owningInfo.getJoinColumns(), owningInfo.getInverseJoinColumns());
    }

    private String extractJoinColumns(Annotation joinTableAnnotation, String propertyName) {
        if (!(joinTableAnnotation instanceof NormalAnnotation)) {
            return null;
        }

        NormalAnnotation normalAnnotation = (NormalAnnotation) joinTableAnnotation;
        for (Object obj : normalAnnotation.values()) {
            MemberValuePair pair = (MemberValuePair) obj;
            String pairName = pair.getName().getIdentifier();
            if (!propertyName.equals(pairName)) {
                continue;
            }

            Expression value = pair.getValue();

            // Handle joinColumns=@JoinColumn(name="bookid")
            if (value instanceof NormalAnnotation) {
                NormalAnnotation joinColumn = (NormalAnnotation) value;
                return extractAnnotationStringProperty(joinColumn, "name");
            }

            // Handle joinColumns={@JoinColumn(name="bookid")} or joinColumns={@JoinColumn(name="bookid"), ...}
            if (value instanceof ArrayInitializer) {
                ArrayInitializer arrayInit = (ArrayInitializer) value;
                if (arrayInit.expressions().size() > 1) {
                    throw new CompositeKeyException("ManyToMany relationships with composite keys are not supported. " +
                            "Found multiple " + propertyName + " in @JoinTable annotation.");
                }

                if (arrayInit.expressions().size() == 1) {
                    Expression joinColumnExpr = (Expression) arrayInit.expressions().get(0);
                    if (joinColumnExpr instanceof NormalAnnotation) {
                        NormalAnnotation joinColumn = (NormalAnnotation) joinColumnExpr;
                        return extractAnnotationStringProperty(joinColumn, "name");
                    }
                }
                return null;
            }
        }
        return null;
    }

    private String extractManyToManyMappedByProperty(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (JpaRelationshipType.fromAnnotationName(annotationType) == JpaRelationshipType.MANY_TO_MANY) {
                String mappedBy = extractAnnotationStringProperty(annotation, "mappedBy");
                if (mappedBy != null) {
                    return mappedBy;
                }
            }
        }
        return null;
    }
}