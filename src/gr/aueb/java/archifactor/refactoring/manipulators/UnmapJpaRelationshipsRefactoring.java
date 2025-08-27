package gr.aueb.java.archifactor.refactoring.manipulators;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.archifactor.util.UnmapJpaRelationshipsUtils;
import gr.aueb.java.jpa.JpaModel;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class UnmapJpaRelationshipsRefactoring extends Refactoring {
    private IJavaProject project;
    private List<RelationshipInfo> relationships;
    private SystemObject systemObject;
    private Map<String, ClassObject> entityMap;
    private Set<String> servicesToCreate;
    
    public UnmapJpaRelationshipsRefactoring(IJavaProject project, List<RelationshipInfo> relationships, SystemObject systemObject) {
        this.project = project;
        this.relationships = relationships;
        this.systemObject = systemObject;
        this.entityMap = new HashMap<>();
        this.servicesToCreate = new HashSet<>();
        
        buildEntityMap();
        identifyRequiredServices();
    }
    
    private void buildEntityMap() {
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (JpaModel.isEntity(classObj)) {
                entityMap.put(classObj.getName(), classObj);
            }
        }
    }
    
    private void identifyRequiredServices() {
        for (RelationshipInfo relationship : relationships) {
            servicesToCreate.add(relationship.getFromEntity());
            servicesToCreate.add(relationship.getToEntity());
        }
    }
    
    @Override
    public String getName() {
        return "Unmap JPA Relationships";
    }
    
    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor monitor) throws OperationCanceledException {
        RefactoringStatus status = new RefactoringStatus();
        
        if (project == null) {
            status.addFatalError("No project selected");
            return status;
        }
        
        if (relationships == null || relationships.isEmpty()) {
            status.addFatalError("No relationships to unmap");
            return status;
        }
        
        if (systemObject == null) {
            status.addFatalError("System object not available");
            return status;
        }
        
        try {
            IProject iProject = project.getProject();
            IMarker[] markers = iProject.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
            for (IMarker marker : markers) {
                Integer severityType = (Integer) marker.getAttribute(IMarker.SEVERITY);
                if (severityType != null && severityType.intValue() == IMarker.SEVERITY_ERROR) {
                    status.addError("Project has compilation errors. Fix them before proceeding.");
                    break;
                }
            }
        } catch (CoreException e) {
            status.addError("Unable to check project compilation status: " + e.getMessage());
        }
        
        return status;
    }
    
    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor monitor) throws OperationCanceledException {
        return new RefactoringStatus();
    }
    
    @Override
    public Change createChange(IProgressMonitor monitor) throws OperationCanceledException {
        CompositeChange compositeChange = new CompositeChange("Unmap JPA Relationships");
        try {
            // 1. Create ServiceFactory first (so entity imports can reference it)
            ServiceFactoryGenerator factoryGenerator = new ServiceFactoryGenerator(project, systemObject);
            String factoryPackage = UnmapJpaRelationshipsUtils.determineServiceFactoryPackage(servicesToCreate, entityMap);
            Change factoryChange = factoryGenerator.createOrUpdateServiceFactory(servicesToCreate, factoryPackage);
            if (factoryChange != null) {
                compositeChange.add(factoryChange);
            }
            
            // 2. Create service interfaces and implementations
            for (String entityName : servicesToCreate) {
                Change serviceInterfaceChange = createServiceInterfaceChange(entityName, monitor);
                Change serviceImplChange = createServiceImplementationChange(entityName, monitor);
                if (serviceInterfaceChange != null) {
                    compositeChange.add(serviceInterfaceChange);
                }
                if (serviceImplChange != null) {
                    compositeChange.add(serviceImplChange);
                }
            }
            
            // 3. Transform entity classes last (so imports reference existing files)
            for (RelationshipInfo relationship : relationships) {
                Change entityChange = createEntityTransformationChange(relationship, monitor);
                if (entityChange != null) {
                    compositeChange.add(entityChange);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new OperationCanceledException("Error creating changes: " + e.getMessage());
        }
        return compositeChange;
    }
    
    private Change createEntityTransformationChange(RelationshipInfo relationship, IProgressMonitor monitor) throws Exception {
        CompositeChange entityChange = new CompositeChange("Transform relationship: " + relationship.getFromEntity() + " -> " + relationship.getToEntity());

        EntityTransformer entityTransformer = new EntityTransformer(systemObject);
        ClassObject fromEntity = entityMap.get(relationship.getFromEntity());
        if (fromEntity == null) {
            throw new IllegalStateException("Entity not found: " + relationship.getFromEntity());
        }
        
        Change change = entityTransformer.transformFromEntity(fromEntity, relationship);
        if (change != null) {
            entityChange.add(change);
        }
        return entityChange.getChildren().length > 0 ? entityChange : null;
    }
    
    private Change createServiceInterfaceChange(String entityName, IProgressMonitor monitor) throws Exception {
        ServiceInterfaceGenerator generator = new ServiceInterfaceGenerator(project, systemObject);
        return generator.createServiceInterface(entityName, UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)));
    }
    
    private Change createServiceImplementationChange(String entityName, IProgressMonitor monitor) throws Exception {
        ServiceImplementationGenerator generator = new ServiceImplementationGenerator(project, systemObject);
        return generator.createServiceImplementation(entityName, UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)));
    }
}