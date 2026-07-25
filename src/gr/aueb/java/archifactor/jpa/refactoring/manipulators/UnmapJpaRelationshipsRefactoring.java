package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.ClassObject;
import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.aueb.java.archifactor.jpa.enums.PersistenceNamespace;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.util.PersistenceNamespaceDetector;
import gr.aueb.java.archifactor.jpa.util.UnmapJpaRelationshipsUtils;
import gr.aueb.java.jpa.JpaModel;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public class UnmapJpaRelationshipsRefactoring extends Refactoring {
    private IJavaProject project;
    private List<RelationshipInfo> relationships;
    private SystemObject systemObject;
    private FrameworkType frameworkType;
    private Map<String, ClassObject> entityMap;
    private PersistenceNamespace persistenceNamespace;
    private Map<String, List<ServiceMethodProvider>> serviceMethodproviders;
    private Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges;
    private Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges;

    public UnmapJpaRelationshipsRefactoring(IJavaProject project, List<RelationshipInfo> relationships, SystemObject systemObject, FrameworkType frameworkType) {
        this.project = project;
        this.relationships = relationships;
        this.systemObject = systemObject;
        this.frameworkType = frameworkType;
        this.entityMap = new HashMap<>();
        this.serviceMethodproviders = new HashMap<>();
        this.compilationUnitChanges = new LinkedHashMap<>();
        this.createCompilationUnitChanges = new LinkedHashMap<>();

        buildEntityMap();
        this.persistenceNamespace = PersistenceNamespaceDetector.detect(entityMap.values());
        identifyRequiredServiceMethods();
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

    private void identifyRequiredServiceMethods() {
        for (RelationshipInfo relationship : relationships) {
			ServiceMethodProvider provider = ServiceMethodProviderFactory.createProvider(relationship);
            if (provider != null) {
                addMethodRequirement(provider.getToEntityName(), provider);
            }
        }
    }

    private void addMethodRequirement(String entityName, ServiceMethodProvider provider) {
        if (!serviceMethodproviders.containsKey(entityName)) {
            serviceMethodproviders.put(entityName, new ArrayList<>());
        }

        List<ServiceMethodProvider> providers = serviceMethodproviders.get(entityName);
        if (!providers.contains(provider)) {
            providers.add(provider);
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
        
        if (frameworkType == null) {
        	status.addFatalError("No framework selected");
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

        if (PersistenceNamespaceDetector.isMixed(entityMap.values())) {
            status.addWarning("The project mixes javax.persistence and jakarta.persistence entities. Generated code will use " + persistenceNamespace.getPrefix() + ".* throughout.");
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
        try {
            Set<String> entitiesNeedingServices = serviceMethodproviders.keySet();

            // 1. Create ServiceFactory first (so entity imports can reference it)
            BaseServiceFactoryGenerator factoryGenerator = ServiceFactoryGeneratorFactory.createGenerator(frameworkType, project, systemObject, persistenceNamespace);
            factoryGenerator.createOrUpdateServiceFactory(
                entitiesNeedingServices, 
                UnmapJpaRelationshipsUtils.determineServiceFactoryPackage(entitiesNeedingServices, entityMap), 
                compilationUnitChanges, 
                createCompilationUnitChanges
            );

            // 2. Create service interfaces and implementations (only required methods)
            ServiceInterfaceGenerator interfaceGenerator = new ServiceInterfaceGenerator(project, systemObject);
            BaseServiceImplementationGenerator implementationGenerator = ServiceImplementationGeneratorFactory.createGenerator(frameworkType, project, systemObject, persistenceNamespace);
            for (Map.Entry<String, List<ServiceMethodProvider>> entry : serviceMethodproviders.entrySet()) {
                String entityName = entry.getKey();
                List<ServiceMethodProvider> providers = entry.getValue();

                interfaceGenerator.createOrUpdateServiceInterface(
                    entityName,
                    providers,
                    UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)),
                    compilationUnitChanges,
                    createCompilationUnitChanges
                );
                implementationGenerator.createOrUpdateServiceImplementation(
                    entityName,
                    providers,
                    UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)),
                    compilationUnitChanges,
                    createCompilationUnitChanges
                );
            }

            // 3. Transform entity classes last (so imports reference existing files)
            EntityTransformer entityTransformer = new EntityTransformer(systemObject, serviceMethodproviders, persistenceNamespace);
            for (RelationshipInfo relationship : relationships) {
                entityTransformer.transformFromEntity(
                	entityMap.get(relationship.getFromEntity()), 
                    relationship, 
                    compilationUnitChanges
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new OperationCanceledException("Error creating changes: " + e.getMessage());
        }

        List<Change> changes = new ArrayList<>();
        changes.addAll(compilationUnitChanges.values());
        changes.addAll(createCompilationUnitChanges.values());
        return new CompositeChange("Unmap JPA Relationships", changes.toArray(new Change[changes.size()]));
    }
}