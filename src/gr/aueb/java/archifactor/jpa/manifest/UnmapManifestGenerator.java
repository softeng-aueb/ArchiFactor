package gr.aueb.java.archifactor.jpa.manifest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.refactoring.manipulators.ServiceMethodProvider;
import gr.aueb.java.archifactor.jpa.refactoring.manipulators.ServiceMethodProviderFactory;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;

/**
 * Generates a machine-readable manifest (JSON) describing what the "Unmap JPA Relationships" 
 * refactoring changed: which relationships were unmapped, the new foreign-key/element-collection 
 * fields that replaced them, and the services that were generated for lazy loading.
 *
 * The manifest is written to the root of the refactored project so that downstream consumers 
 * could know exactly what changed.
 */
public class UnmapManifestGenerator {
    public static final String MANIFEST_FILE_NAME = "archifactor-manifest.json";

    public static String generateJson(List<RelationshipInfo> relationships, FrameworkType frameworkType) {
        Map<String, Set<String>> serviceMethodsByEntity = aggregateServiceMethodsByEntity(relationships);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"tool\": \"ArchiFactor\",\n");
        sb.append("  \"manifestVersion\": 1,\n");
        sb.append("  \"refactoring\": \"Unmap JPA Relationships\",\n");
        sb.append("  \"description\": \"The JPA relationship mappings listed below were unmapped: each association field was made @Transient and is now lazy-loaded through a generated service. Owning sides keep the relationship data in a new foreign-key or element-collection field. The database schema is unchanged, so native SQL queries are unaffected; only object-model queries (JPQL, Criteria API, derived query methods) that traverse the unmapped associations are broken. Relationships that declared cascade or orphanRemoval additionally lose that lifecycle propagation, recorded per relationship under 'droppedSemantics'.\",\n");
        sb.append("  \"generatedAt\": ").append(quote(Instant.now().toString())).append(",\n");
        sb.append("  \"framework\": ").append(quote(frameworkType.getDisplayName())).append(",\n");
        sb.append("  \"serviceFactoryClass\": ").append(quote(serviceFactoryClass(serviceMethodsByEntity.keySet()))).append(",\n");

        sb.append("  \"unmappedRelationships\": [\n");
        for (int i = 0; i < relationships.size(); i++) {
            appendRelationship(sb, relationships.get(i), i < relationships.size() - 1);
        }
        sb.append("  ],\n");

        sb.append("  \"generatedServices\": [\n");
        int index = 0;
        for (Map.Entry<String, Set<String>> entry : serviceMethodsByEntity.entrySet()) {
            appendService(sb, entry.getKey(), entry.getValue(), index < serviceMethodsByEntity.size() - 1);
            index++;
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static void writeToProject(IJavaProject project, String json) throws CoreException {
        IFile file = project.getProject().getFile(MANIFEST_FILE_NAME);
        ByteArrayInputStream contents = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        if (file.exists()) {
            file.setContents(contents, true, false, null);
        } else {
            file.create(contents, true, null);
        }
    }

    private static void appendRelationship(StringBuilder sb, RelationshipInfo relationship, boolean trailingComma) {
        sb.append("    {\n");
        sb.append("      \"fromEntity\": ").append(quote(relationship.getFromEntity())).append(",\n");
        sb.append("      \"fieldName\": ").append(quote(relationship.getFieldName())).append(",\n");
        sb.append("      \"relationshipType\": ").append(quote(relationship.getRelationshipType().getAnnotationName())).append(",\n");
        sb.append("      \"toEntity\": ").append(quote(relationship.getToEntity())).append(",\n");
        sb.append("      \"toEntityIdField\": ").append(quote(relationship.getReferencedPkName())).append(",\n");
        sb.append("      \"owningSide\": ").append(relationship.isOwningSide());

        if (relationship.isOwningSide()) {
            if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY) {
                sb.append(",\n");
                sb.append("      \"newElementCollectionField\": {\n");
                sb.append("        \"name\": ").append(quote(relationship.getJoinTableInverseJoinColumns() + "s")).append(",\n");
                sb.append("        \"type\": ").append(quote("Set<" + UnmapJpaRelationshipsUtils.getSimpleTypeName(relationship.getReferencedPkType()) + ">")).append(",\n");
                sb.append("        \"collectionTable\": ").append(quote(relationship.getJoinTableName())).append(",\n");
                sb.append("        \"joinColumn\": ").append(quote(relationship.getJoinTableJoinColumns())).append(",\n");
                sb.append("        \"valueColumn\": ").append(quote(relationship.getJoinTableInverseJoinColumns())).append("\n");
                sb.append("      }");
            } else {
                sb.append(",\n");
                sb.append("      \"newForeignKeyField\": {\n");
                sb.append("        \"name\": ").append(quote(relationship.getJoinColumnName())).append(",\n");
                sb.append("        \"type\": ").append(quote(UnmapJpaRelationshipsUtils.getSimpleTypeName(relationship.getReferencedPkType()))).append(",\n");
                sb.append("        \"columnName\": ").append(quote(relationship.getJoinColumnName())).append("\n");
                sb.append("      }");
            }
        } else {
            // For inverse sides the relationship data now lives on the target
            // entity; record where, so each entry is self-contained.
            if (relationship.getRelationshipType() == JpaRelationshipType.ONE_TO_MANY) {
                sb.append(",\n");
                sb.append("      \"targetForeignKeyField\": ").append(quote(relationship.getJoinColumnName()));
            } else if (relationship.getRelationshipType() == JpaRelationshipType.MANY_TO_MANY) {
                sb.append(",\n");
                sb.append("      \"targetElementCollectionField\": ").append(quote(relationship.getJoinTableInverseJoinColumns() + "s"));
            }
        }

        if (relationship.hasDroppedSemantics()) {
            sb.append(",\n");
            sb.append("      \"droppedSemantics\": {\n");
            sb.append("        \"cascade\": [");
            int cascadeIndex = 0;
            for (String cascadeType : relationship.getCascadeTypes()) {
                sb.append(cascadeIndex > 0 ? ", " : "").append(quote(cascadeType));
                cascadeIndex++;
            }
            sb.append("],\n");
            sb.append("        \"orphanRemoval\": ").append(relationship.isOrphanRemoval()).append(",\n");
            sb.append("        \"note\": ").append(quote("The ORM no longer propagates these lifecycle operations. "
                + "The database foreign-key constraints are unchanged, so a parent delete may now fail or leave orphan rows. "
                + "A test failing because of this is a semantics loss, not a broken query.")).append("\n");
            sb.append("      }");
        }

        ServiceMethodProvider provider = ServiceMethodProviderFactory.createProvider(relationship);
        if (provider != null) {
            sb.append(",\n");
            sb.append("      \"lazyLoadServiceMethod\": {\n");
            sb.append("        \"service\": ").append(quote(serviceInterfaceName(relationship.getToEntity()))).append(",\n");
            sb.append("        \"method\": ").append(quote(provider.getMethodDeclaration())).append("\n");
            sb.append("      }");
        }

        sb.append("\n    }").append(trailingComma ? "," : "").append("\n");
    }

    private static void appendService(StringBuilder sb, String entityName, Set<String> methods, boolean trailingComma) {
        sb.append("    {\n");
        sb.append("      \"entity\": ").append(quote(entityName)).append(",\n");
        sb.append("      \"interface\": ").append(quote(serviceInterfaceName(entityName))).append(",\n");
        sb.append("      \"implementation\": ").append(quote(serviceInterfaceName(entityName) + "Impl")).append(",\n");
        sb.append("      \"methods\": [\n");
        int index = 0;
        for (String method : methods) {
            sb.append("        ").append(quote(method)).append(index < methods.size() - 1 ? "," : "").append("\n");
            index++;
        }
        sb.append("      ]\n");
        sb.append("    }").append(trailingComma ? "," : "").append("\n");
    }

    /**
     * Aggregates the required service methods per target entity, mirroring
     * UnmapJpaRelationshipsRefactoring.identifyRequiredServiceMethods().
     */
    private static Map<String, Set<String>> aggregateServiceMethodsByEntity(List<RelationshipInfo> relationships) {
        Map<String, Set<String>> serviceMethodsByEntity = new LinkedHashMap<>();
        for (RelationshipInfo relationship : relationships) {
            ServiceMethodProvider provider = ServiceMethodProviderFactory.createProvider(relationship);
            if (provider != null) {
                if (!serviceMethodsByEntity.containsKey(provider.getToEntityName())) {
                    serviceMethodsByEntity.put(provider.getToEntityName(), new LinkedHashSet<>());
                }
                serviceMethodsByEntity.get(provider.getToEntityName()).add(provider.getMethodDeclaration());
            }
        }
        return serviceMethodsByEntity;
    }

    /**
     * Mirrors the naming used by ServiceInterfaceGenerator: the interface is
     * named <SimpleEntityName>Service and placed in the entity's package.
     */
    private static String serviceInterfaceName(String entityFqn) {
        String simpleName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityFqn);
        int lastDot = entityFqn.lastIndexOf('.');
        if (lastDot >= 0) {
            return entityFqn.substring(0, lastDot) + "." + simpleName + "Service";
        }
        return simpleName + "Service";
    }

    /**
     * The ServiceFactory is placed in the common ancestor package of the entities that needed services, 
     * as determined by UnmapJpaRelationshipsUtils.determineServiceFactoryPackage().
     */
    private static String serviceFactoryClass(Set<String> serviceEntityFqns) {
        if (serviceEntityFqns.isEmpty()) {
            return null;
        }
        String commonPackage = UnmapJpaRelationshipsUtils.determineServiceFactoryPackage(serviceEntityFqns);
        if (commonPackage == null || commonPackage.isEmpty()) {
            return "ServiceFactory";
        }
        return commonPackage + ".ServiceFactory";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append("\"").toString();
    }
}
