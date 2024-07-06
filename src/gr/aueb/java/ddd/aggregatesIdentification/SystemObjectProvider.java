package gr.aueb.java.ddd.aggregatesIdentification;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.SystemObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class SystemObjectProvider {

    @SuppressWarnings("unlikely-arg-type")
	public static SystemObject getSystemObject() throws JavaModelException {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
		    try {
				if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
				    IJavaProject javaProject = JavaCore.create(project);
				    if (ASTReader.getSystemObject() == null || !project.equals(ASTReader.getExaminedProject())) {
				        new ASTReader(javaProject, new NullProgressMonitor());
				    }
				    return ASTReader.getSystemObject();
				}
			} catch (CoreException e) {
				e.printStackTrace();
			} catch (CompilationErrorDetectedException e) {
				e.printStackTrace();
			}
		}
        return null;
    }
}
