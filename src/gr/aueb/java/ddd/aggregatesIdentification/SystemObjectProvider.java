package gr.aueb.java.ddd.aggregatesIdentification;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.SystemObject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

public class SystemObjectProvider {

    public static SystemObject getSystemObject(IJavaProject project) throws JavaModelException {
    	try {
		    if (ASTReader.getSystemObject() == null || !project.equals(ASTReader.getExaminedProject())) {
		        new ASTReader(project, new NullProgressMonitor());
		    }
			    return ASTReader.getSystemObject();
		} catch (CompilationErrorDetectedException e) {
			e.printStackTrace();
		}
        return null;
    }
}
