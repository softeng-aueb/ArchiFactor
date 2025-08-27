package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.core.resources.IFile;
import org.eclipse.text.edits.ReplaceEdit;

import gr.uom.java.ast.SystemObject;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;

public class ServiceImplementationGenerator {
    private IJavaProject project;
    private SystemObject systemObject;
    
    public ServiceImplementationGenerator(IJavaProject project, SystemObject systemObject) {
        this.project = project;
        this.systemObject = systemObject;
    }
    
    public Change createServiceImplementation(String entityName, String servicePackage) throws JavaModelException {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceImplName = simpleEntityName + "ServiceImpl";
        String serviceImplFileName = serviceImplName + ".java";

        IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
        if (packageFragment == null) {
            return null;
        }

        ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceImplFileName);
        if (existingCU.exists()) {
            return null; // Don't overwrite existing service
        }

        // Create the service implementation content
        String serviceImplContent = generateServiceImplementationContent(entityName, servicePackage);
        
        // Create empty compilation unit first
        ICompilationUnit newCU = packageFragment.createCompilationUnit(serviceImplFileName, "", false, null);
        IFile file = (IFile) newCU.getResource();
        
        // Create a change that replaces the empty content with the service implementation content
        TextFileChange change = new TextFileChange("Create " + serviceImplName + " implementation", file);
        change.setEdit(new ReplaceEdit(0, 0, serviceImplContent));
        return change;
    }
    
    private String generateServiceImplementationContent(String entityName, String packageName) {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        JpaAnnotationExtractor extractor = new JpaAnnotationExtractor(systemObject);
        String pkType = UnmapJpaRelationshipsUtils.getSimpleTypeName(extractor.extractIdFieldType(entityName));

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");
        String entityPackage = UnmapJpaRelationshipsUtils.determineEntityPackage(systemObject, entityName);
        if (entityPackage != null && !entityPackage.equals(packageName)) {
            content.append("import ").append(entityName).append(";\n");  // entityName is already FQN
        }
        content.append("import java.util.List;\n");
        content.append("import java.util.ArrayList;\n");
        content.append("import java.util.UUID;\n");
        content.append("import jakarta.inject.Inject;\n");
        content.append("import jakarta.inject.Singleton;\n");
        content.append("import jakarta.persistence.EntityManager;\n\n");
        content.append("@Singleton\n");
        content.append("public class ").append(simpleEntityName).append("ServiceImpl implements ").append(simpleEntityName).append("Service {\n");
        content.append("    @Inject\n");
        content.append("    private EntityManager entityManager;\n\n");
        content.append("    @Override\n");
        content.append("    public ").append(simpleEntityName).append(" get").append(simpleEntityName).append("ById(").append(pkType).append(" id) {\n");
        content.append("        return entityManager.find(").append(simpleEntityName).append(".class, id);\n");
        content.append("    }\n\n");
        generateForeignKeyImplementations(content, simpleEntityName);
        content.append("    private List<").append(simpleEntityName).append("> get").append(simpleEntityName).append("sByForeignKeyInternal(String fieldName, Object foreignKeyId) {\n");
        content.append("        if (fieldName == null || foreignKeyId == null) {\n");
        content.append("            return new ArrayList<>();\n");
        content.append("        }\n");
        content.append("        String query = \"SELECT e FROM ").append(simpleEntityName).append(" e WHERE e.\" + fieldName + \" = :foreignKeyId\";\n");
        content.append("        return entityManager.createQuery(query, ").append(simpleEntityName).append(".class)\n");
        content.append("                .setParameter(\"foreignKeyId\", foreignKeyId)\n");
        content.append("                .getResultList();\n");
        content.append("    }\n");
        content.append("}\n");
        return content.toString();
    }
    
    private void generateForeignKeyImplementations(StringBuilder content, String simpleEntityName) {
        String[] fkTypes = {"Integer", "Long", "UUID"};
        for (String fkType : fkTypes) {
            content.append("    @Override\n");
            content.append("    public List<").append(simpleEntityName).append("> get")
                   .append(simpleEntityName).append("sByForeignKey(String fieldName, ")
                   .append(fkType).append(" foreignKeyId) {\n");
            content.append("        return get").append(simpleEntityName).append("sByForeignKeyInternal(fieldName, foreignKeyId);\n");
            content.append("    }\n\n");
        }
    }
}