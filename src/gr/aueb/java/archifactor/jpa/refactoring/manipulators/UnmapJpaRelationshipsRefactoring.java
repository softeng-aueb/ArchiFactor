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
import gr.aueb.java.archifactor.jpa.enums.JpaRelationshipType;
import gr.aueb.java.archifactor.jpa.enums.ServiceMethodType;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.aueb.java.archifactor.jpa.model.ServiceMethodRequirementInfo;
import gr.aueb.java.archifactor.jpa.util.JpaAnnotationExtractorUtils;
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
    private Map<String, List<ServiceMethodRequirementInfo>> serviceMethodRequirements;
    private Map<ICompilationUnit, CompilationUnitChange> compilationUnitChanges;
    private Map<ICompilationUnit, CreateCompilationUnitChange> createCompilationUnitChanges;

    public UnmapJpaRelationshipsRefactoring(IJavaProject project, List<RelationshipInfo> relationships, SystemObject systemObject, FrameworkType frameworkType) {
        this.project = project;
        this.relationships = relationships;
        this.systemObject = systemObject;
        this.frameworkType = frameworkType;
        this.entityMap = new HashMap<>();
        this.serviceMethodRequirements = new HashMap<>();
        this.compilationUnitChanges = new LinkedHashMap<>();
        this.createCompilationUnitChanges = new LinkedHashMap<>();

        buildEntityMap();
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

    /**
     * Analyzes all relationships to determine which service methods are needed for lazy loading.
     * ManyToOne: needs getXById(Y) where X is toEntity, and Y is toEntity's @Id type
     * OneToMany: needs getXsByY(Z) where X is toEntity, Y is toEntity's FK field name, and Z is fromEntity's @Id type
     * ManyToMany owning: needs getXsByIds(Y) where X is toEntity, and Y is a Collection of toEntity's @Id type
     * ManyToMany non-owning: needs getXsByYId(Z) where X is toEntity, Y is fromEntity, and Z is fromEntity's @Id type
     */
    private void identifyRequiredServiceMethods() {
        for (RelationshipInfo relationship : relationships) {
            JpaRelationshipType relationshipType = relationship.getRelationshipType();

            if (relationshipType == JpaRelationshipType.MANY_TO_ONE) {
                addManyToOneRequirements(relationship);
            } else if (relationshipType == JpaRelationshipType.ONE_TO_MANY) {
                addOneToManyRequirements(relationship);
            } else if (relationshipType == JpaRelationshipType.MANY_TO_MANY) {
                if (relationship.isOwningSide()) {
                    addManyToManyOwningRequirements(relationship);
                } else {
                    addManyToManyNonOwningRequirements(relationship);
                }
            }
        }
    }

    private void addManyToOneRequirements(RelationshipInfo relationship) {
        String toEntity = relationship.getToEntity();
        String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
        String toEntityIdType = relationship.getReferencedPkType();
        String toEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(toEntityIdType);

        String methodName = "get" + toEntitySimple + "ById";

        ServiceMethodRequirementInfo requirement = new ServiceMethodRequirementInfo(
            toEntity,
            null,
            ServiceMethodType.GET_BY_ID,
            toEntityIdTypeSimple,
            toEntitySimple,
            null,
            methodName,
            null,
            null
        );

        addMethodRequirement(toEntity, requirement);
    }

    private void addOneToManyRequirements(RelationshipInfo relationship) {
        String toEntity = relationship.getToEntity();
        String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);

        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(systemObject);
        String fromEntity = relationship.getFromEntity();
        String fromEntityIdType = jpaExtractor.extractIdFieldType(fromEntity);
        String fromEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(fromEntityIdType);

        String fkFieldName = relationship.getJoinColumnName();
        String methodName = "get" + toEntitySimple + "sBy" + UnmapJpaRelationshipsUtils.capitalize(fkFieldName);

        ServiceMethodRequirementInfo requirement = new ServiceMethodRequirementInfo(
            toEntity,
            null,
            ServiceMethodType.GET_BY_FOREIGN_KEY,
            fromEntityIdTypeSimple,
            "List<" + toEntitySimple + ">",
            fkFieldName,
            methodName,
            null,
            null
        );

        addMethodRequirement(toEntity, requirement);
    }

    private void addManyToManyOwningRequirements(RelationshipInfo relationship) {
        String toEntity = relationship.getToEntity();
        String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);
        String toEntityIdType = relationship.getReferencedPkType();
        String toEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(toEntityIdType);
        String toEntityIdFieldName = relationship.getReferencedPkName();

        String methodName = "get" + toEntitySimple + "sByIds";

        ServiceMethodRequirementInfo requirement = new ServiceMethodRequirementInfo(
            toEntity,
            null,
            ServiceMethodType.GET_BY_MANY_TO_MANY_OWNING,
            toEntityIdTypeSimple,
            "List<" + toEntitySimple + ">",
            null,
            methodName,
            toEntityIdFieldName,
            null
        );

        addMethodRequirement(toEntity, requirement);
    }

    private void addManyToManyNonOwningRequirements(RelationshipInfo relationship) {
        String toEntity = relationship.getToEntity();
        String toEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(toEntity);

        String fromEntity = relationship.getFromEntity();
        String fromEntitySimple = UnmapJpaRelationshipsUtils.getSimpleClassName(fromEntity);

        JpaAnnotationExtractorUtils jpaExtractor = new JpaAnnotationExtractorUtils(systemObject);
        String fromEntityIdType = jpaExtractor.extractIdFieldType(fromEntity);
        String fromEntityIdTypeSimple = UnmapJpaRelationshipsUtils.getSimpleTypeName(fromEntityIdType);

        String methodName = "get" + toEntitySimple + "sBy" + fromEntitySimple + "Id";

        String joinTableInverseJoinColumns = relationship.getJoinTableInverseJoinColumns();

        ServiceMethodRequirementInfo requirement = new ServiceMethodRequirementInfo(
            toEntity,
            fromEntitySimple,
            ServiceMethodType.GET_BY_MANY_TO_MANY_NON_OWNING,
            fromEntityIdTypeSimple,
            "List<" + toEntitySimple + ">",
            null,
            methodName,
            null,
            joinTableInverseJoinColumns
        );

        addMethodRequirement(toEntity, requirement);
    }

    private void addMethodRequirement(String entityName, ServiceMethodRequirementInfo requirement) {
        if (!serviceMethodRequirements.containsKey(entityName)) {
            serviceMethodRequirements.put(entityName, new ArrayList<>());
        }

        List<ServiceMethodRequirementInfo> requirements = serviceMethodRequirements.get(entityName);
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
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
        try {
            Set<String> entitiesNeedingServices = serviceMethodRequirements.keySet();

            // 1. Create ServiceFactory first (so entity imports can reference it)
            BaseServiceFactoryGenerator factoryGenerator = ServiceFactoryGeneratorFactory.createGenerator(frameworkType, project, systemObject);
            String factoryPackage = UnmapJpaRelationshipsUtils.determineServiceFactoryPackage(entitiesNeedingServices, entityMap);
            factoryGenerator.createOrUpdateServiceFactory(entitiesNeedingServices, factoryPackage, compilationUnitChanges, createCompilationUnitChanges);

            // 2. Create service interfaces and implementations (only required methods)
            for (Map.Entry<String, List<ServiceMethodRequirementInfo>> entry : serviceMethodRequirements.entrySet()) {
                String entityName = entry.getKey();
                List<ServiceMethodRequirementInfo> requirements = entry.getValue();

                createServiceInterfaceChange(entityName, requirements);
                createServiceImplementationChange(entityName, requirements);
            }

            // 3. Transform entity classes last (so imports reference existing files)
            EntityTransformer entityTransformer = new EntityTransformer(systemObject, serviceMethodRequirements);
            for (RelationshipInfo relationship : relationships) {
                createEntityTransformationChange(relationship, entityTransformer);
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

    private void createEntityTransformationChange(RelationshipInfo relationship, EntityTransformer entityTransformer) throws Exception {
        ClassObject fromEntity = entityMap.get(relationship.getFromEntity());
        if (fromEntity == null) {
            throw new IllegalStateException("Entity not found: " + relationship.getFromEntity());
        }

        entityTransformer.transformFromEntity(fromEntity, relationship, compilationUnitChanges);
    }

    private void createServiceInterfaceChange(String entityName, List<ServiceMethodRequirementInfo> requirements) throws Exception {
        ServiceInterfaceGenerator generator = new ServiceInterfaceGenerator(project, systemObject);
        generator.createOrUpdateServiceInterface(
            entityName, 
            requirements, 
            UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)),
            compilationUnitChanges, 
            createCompilationUnitChanges
        );
    }

    private void createServiceImplementationChange(String entityName, List<ServiceMethodRequirementInfo> requirements) throws Exception {
        BaseServiceImplementationGenerator generator = ServiceImplementationGeneratorFactory.createGenerator(frameworkType, project, systemObject);
        generator.createOrUpdateServiceImplementation(
            entityName, 
            requirements, 
            UnmapJpaRelationshipsUtils.getPackageNameFromClass(entityMap.get(entityName)), 
            compilationUnitChanges, 
            createCompilationUnitChanges
        );
    }
}