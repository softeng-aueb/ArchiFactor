package gr.uom.java.jdeodorant.refactoring.manipulators;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class MoveClassRefactoring {
	
	private MoveDescriptor descriptor;
	private ICompilationUnit[] ClassUnits;
	private String projectName;
	private IJavaElement destination;
	private boolean updateReferences;
	private IFile[] files = {};
	private IFolder[] folders = {};
	private RefactoringStatus status;
	//private RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE);
	
	public MoveClassRefactoring(MoveDescriptor descriptor, ICompilationUnit[] ClassUnits, String projectName, IJavaElement destination, boolean updateReferences, RefactoringStatus status)
	{
		this.descriptor = descriptor;
		this.ClassUnits = ClassUnits;
		this.projectName = projectName;
		this.destination = destination;
		this.updateReferences = updateReferences;
		this.status = status;
		this.descriptor.setProject(projectName);
		this.descriptor.setDestination(destination);
		this.descriptor.setUpdateReferences(updateReferences);
		this.descriptor.setMoveResources(this.files, this.folders, ClassUnits);
		
	}
	
	public Refactoring getRefactoring() {
		Refactoring refactoring = null;
		try {
			refactoring = descriptor.createRefactoring(this.status);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return refactoring;
	}

}
