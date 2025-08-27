package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Expression;

import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.SystemObject;

import java.util.Iterator;
import java.util.ListIterator;

public class JpaAnnotationExtractor {
    private SystemObject systemObject;
    
    public JpaAnnotationExtractor(SystemObject systemObject) {
        this.systemObject = systemObject;
    }

    public String extractJoinColumnName(FieldObject field) {
        // First check if this field has a direct @JoinColumn annotation
        for (Annotation annotation : field.getAnnotations()) {
            String annotationType = annotation.getTypeName().getFullyQualifiedName();
            if (annotationType.equals("JoinColumn")) {
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
            if (annotationType.equals("OneToMany") || annotationType.equals("OneToOne")) {
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
            if (annotationType.equals("JoinColumn")) {
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
}