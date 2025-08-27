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

public class ServiceInterfaceGenerator {
    private IJavaProject project;
    private SystemObject systemObject;
    
    public ServiceInterfaceGenerator(IJavaProject project, SystemObject systemObject) {
        this.project = project;
        this.systemObject = systemObject;
    }
    
    public Change createServiceInterface(String entityName, String servicePackage) throws JavaModelException {
        String simpleEntityName = UnmapJpaRelationshipsUtils.getSimpleClassName(entityName);
        String serviceName = simpleEntityName + "Service";
        String serviceFileName = serviceName + ".java";

        IPackageFragment packageFragment = UnmapJpaRelationshipsUtils.findOrCreatePackage(project, servicePackage);
        if (packageFragment == null) {
            return null;
        }

        ICompilationUnit existingCU = packageFragment.getCompilationUnit(serviceFileName);
        if (existingCU.exists()) {
            return null; // Don't overwrite existing service
        }

        // Create the service interface content
        String serviceInterfaceContent = generateServiceInterfaceContent(entityName, servicePackage);

        // Create empty compilation unit first
        ICompilationUnit newCU = packageFragment.createCompilationUnit(serviceFileName, "", false, null);
        IFile file = (IFile) newCU.getResource();

        // Create a change that replaces the empty content with the interface content
        TextFileChange change = new TextFileChange("Create " + serviceName + " interface", file);
        change.setEdit(new ReplaceEdit(0, 0, serviceInterfaceContent));
        return change;
    }

    private String generateServiceInterfaceContent(String entityName, String packageName) {
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
        content.append("import java.util.UUID;\n\n");
        content.append("public interface ").append(simpleEntityName).append("Service {\n");
        content.append("    ").append(simpleEntityName).append(" get").append(simpleEntityName).append("ById(").append(pkType).append(" id);\n\n");
        generateForeignKeyMethods(content, simpleEntityName);
        content.append("}\n");
        return content.toString();
    }

    private void generateForeignKeyMethods(StringBuilder content, String simpleEntityName) {
        String[] fkTypes = {"Integer", "Long", "UUID"};
        for (String fkType : fkTypes) {
            content.append("    List<").append(simpleEntityName).append("> get")
                   .append(simpleEntityName).append("sByForeignKey(String fieldName, ")
                   .append(fkType).append(" foreignKeyId);\n");
        }
    }
}