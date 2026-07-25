package gr.aueb.java.archifactor.jpa.util;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
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
import gr.uom.java.ast.TypeObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class JpaAnnotationExtractorUtils {
    private SystemObject systemObject;
    
    public JpaAnnotationExtractorUtils(SystemObject systemObject) {
        this.systemObject = systemObject;
    }

    public String extractJoinColumnName(FieldObject field, boolean isOwningSide) {
        if (isOwningSide) {
            return extractJoinColumnNameForOwningSide(field);
        } else {
            return extractJoinColumnNameForInverseSide(field);
        }
    }

    private String extractJoinColumnNameForOwningSide(FieldObject field) {
        String joinColumnName = extractDirectJoinColumnName(field);
        if (joinColumnName != null) {
            return joinColumnName;
        }

        String referencedEntityType = getEntityTypeFromField(field);
        String referencedPkColumnName = extractIdColumnName(referencedEntityType);
        if (referencedPkColumnName != null) {
            return field.getName() + "_" + referencedPkColumnName;
        }

        return null;
    }

    private String extractJoinColumnNameForInverseSide(FieldObject field) {
        String mappedByFieldName = extractMappedByFromRelationship(field);
        if (mappedByFieldName == null) {
            return null;
        }

        String entityWithOwningFieldType = getEntityTypeFromField(field);
        ClassObject entityWithOwningField = findEntityByTypeName(entityWithOwningFieldType);
        if (entityWithOwningField == null) {
            return null;
        }

        FieldObject owningField = findFieldInEntity(entityWithOwningField, mappedByFieldName);
        if (owningField == null) {
            return null;
        }

        String joinColumnName = extractDirectJoinColumnName(owningField);
        if (joinColumnName != null) {
            return joinColumnName;
        }

        String inputFieldEntityType = getEntityTypeFromField(owningField);
        String referencedPkColumnName = extractIdColumnName(inputFieldEntityType);
        if (referencedPkColumnName != null) {
            return owningField.getName() + "_" + referencedPkColumnName;
        }

        return null;
    }

    private String getEntityTypeFromField(FieldObject field) {
        String entityType = field.getType().getGenericType();
        if (entityType != null) {
            return entityType.replaceAll("[<>]", "").trim();
        }
        return field.getType().getClassType();
    }

    public String extractMappedByFromRelationship(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (JpaRelationshipType.isRelationshipType(annotationType)) {
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
        for (ClassObject classObj : getMappedHierarchy(entity.getName())) {
            Iterator<FieldObject> fieldIterator = classObj.getFieldIterator();
            while (fieldIterator.hasNext()) {
                FieldObject field = fieldIterator.next();
                if (fieldName.equals(field.getName())) {
                    return field;
                }
            }
        }
        return null;
    }

    /**
     * The entity class plus the superclasses, so lookups also see the fields a base entity declares. 
     * JPA only maps superclass state when the superclass is a @MappedSuperclass or an @Entity, so a plain class ends the walk.
     */
    public List<ClassObject> getMappedHierarchy(String entityName) {
        List<ClassObject> hierarchy = new ArrayList<ClassObject>();
        ClassObject classObj = systemObject.getClassObject(entityName);
        while (classObj != null) {
            hierarchy.add(classObj);

            TypeObject superclass = classObj.getSuperclass();
            if (superclass == null) {
                break;
            }

            ClassObject superclassObj = systemObject.getClassObject(superclass.getClassType());
            if (superclassObj == null || !(hasClassAnnotation(superclassObj, "MappedSuperclass") || hasClassAnnotation(superclassObj, "Entity"))) {
                break;
            }
            classObj = superclassObj;
        }
        return hierarchy;
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
        return findIdField(entityName).getType().getClassType();
    }

    public String extractIdFieldName(String entityName) {
        return findIdField(entityName).getName();
    }

    public String extractIdColumnName(String entityName) {
        FieldObject idField = findIdField(entityName);
        String columnName = extractColumnName(idField);
        return columnName != null ? columnName : idField.getName();
    }

    private FieldObject findIdField(String entityName) {
        for (ClassObject classObj : getMappedHierarchy(entityName)) {
            // With @IdClass the primary key is spread over several @Id fields, so returning the
            // first one would produce a foreign key and a lookup based on only part of the key.
            if (hasClassAnnotation(classObj, "IdClass")) {
                throw new CompositeKeyException("Entity " + entityName + " has a composite primary key (@IdClass), which is not supported.");
            }

            Iterator<FieldObject> fieldIterator = classObj.getFieldIterator();
            while (fieldIterator.hasNext()) {
                FieldObject field = fieldIterator.next();
                if (hasFieldAnnotation(field, "Id")) {
                    return field;
                }
                if (hasFieldAnnotation(field, "EmbeddedId")) {
                    throw new CompositeKeyException("Entity " + entityName + " has a composite primary key (@EmbeddedId), which is not supported.");
                }
            }
        }

        throw new IllegalStateException("No @Id field was found in " + entityName + " or in its mapped superclasses.");
    }

    private String extractColumnName(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (matchesAnnotationSimpleName(annotation, "Column")) {
                String columnName = extractAnnotationStringProperty(annotation, "name");
                if (columnName != null) {
                    return columnName;
                }
            }
        }
        return null;
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

    public static boolean hasClassAnnotation(ClassObject classObject, String annotationSimpleName) {
        for (Annotation annotation : classObject.getAnnotations()) {
            if (matchesAnnotationSimpleName(annotation, annotationSimpleName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasFieldAnnotation(FieldObject field, String annotationSimpleName) {
        for (Annotation annotation : field.getAnnotations()) {
            if (matchesAnnotationSimpleName(annotation, annotationSimpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnnotationSimpleName(Annotation annotation, String annotationSimpleName) {
        String fqn = annotation.getTypeName().getFullyQualifiedName();
        if (fqn == null) {
            return false;
        }
        int lastDot = fqn.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        return annotationSimpleName.equals(simpleName);
    }

    private static final Set<String> OWNERSHIP_CASCADE_TYPES =
        new HashSet<String>(Arrays.asList("ALL", "PERSIST", "REMOVE", "MERGE"));

    public static boolean hasOwnershipCascade(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (!JpaRelationshipType.isRelationshipType(annotationType)) {
                continue;
            }

            for (String cascade : extractCascadeTypes(annotation)) {
                if (OWNERSHIP_CASCADE_TYPES.contains(cascade)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> extractCascadeTypes(Annotation annotation) {
        Set<String> cascadeTypes = new HashSet<String>();
        if (!(annotation instanceof NormalAnnotation)) {
            return cascadeTypes;
        }

        NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
        for (Object obj : normalAnnotation.values()) {
            MemberValuePair pair = (MemberValuePair) obj;
            if (!"cascade".equals(pair.getName().getIdentifier())) {
                continue;
            }

            Expression value = pair.getValue();
            if (value instanceof ArrayInitializer) {
                ArrayInitializer arrayInit = (ArrayInitializer) value;
                for (Object exprObj : arrayInit.expressions()) {
                    String cascadeName = extractCascadeEnumName((Expression) exprObj);
                    if (cascadeName != null) {
                        cascadeTypes.add(cascadeName);
                    }
                }
            } else {
                String cascadeName = extractCascadeEnumName(value);
                if (cascadeName != null) {
                    cascadeTypes.add(cascadeName);
                }
            }
        }
        return cascadeTypes;
    }

    private static String extractCascadeEnumName(Expression expression) {
        if (expression instanceof QualifiedName) {
            return ((QualifiedName) expression).getName().getIdentifier();
        }
        if (expression instanceof SimpleName) {
            return ((SimpleName) expression).getIdentifier();
        }
        return null;
    }

    public static boolean hasOrphanRemoval(FieldObject field) {
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (!JpaRelationshipType.isRelationshipType(annotationType)) {
                continue;
            }

            if (extractBooleanProperty(annotation, "orphanRemoval")) {
                return true;
            }
        }
        return false;
    }

    private static boolean extractBooleanProperty(Annotation annotation, String propertyName) {
        if (!(annotation instanceof NormalAnnotation)) {
            return false;
        }

        NormalAnnotation normalAnnotation = (NormalAnnotation) annotation;
        for (Object obj : normalAnnotation.values()) {
            MemberValuePair pair = (MemberValuePair) obj;
            if (!propertyName.equals(pair.getName().getIdentifier())) {
                continue;
            }

            Expression value = pair.getValue();
            if (value instanceof BooleanLiteral) {
                return ((BooleanLiteral) value).booleanValue();
            }
        }
        return false;
    }
}