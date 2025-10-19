package gr.aueb.java.archifactor.jpa.refactoring.manipulators;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import gr.aueb.java.archifactor.jpa.enums.FrameworkType;
import gr.aueb.java.archifactor.jpa.model.RelationshipInfo;
import gr.uom.java.ast.SystemObject;

import java.util.List;

public class UnmapJpaRelationshipsRefactoringDescriptor extends RefactoringDescriptor {
    public static final String REFACTORING_ID = "gr.aueb.java.archifactor.refactoring.unmapJpaRelationships";
    
    private IJavaProject project;
    private List<RelationshipInfo> relationships;
    private SystemObject systemObject;
    private FrameworkType frameworkType;
    
    public UnmapJpaRelationshipsRefactoringDescriptor(IJavaProject project, List<RelationshipInfo> relationships, SystemObject systemObject, FrameworkType frameworkType) {
        super(REFACTORING_ID, null, "Unmap JPA Relationships", null, RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE);
        this.project = project;
        this.relationships = relationships;
        this.systemObject = systemObject;
        this.frameworkType = frameworkType;
    }
    
    @Override
    public Refactoring createRefactoring(RefactoringStatus status) throws CoreException {
        return new UnmapJpaRelationshipsRefactoring(project, relationships, systemObject, frameworkType);
    }
}