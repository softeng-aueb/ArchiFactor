package gr.aueb.java.archifactor.util;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;

public class JavaModelUtil {

	public static boolean hasContent(IJavaElement packageFragment) {
		try {
			return ((IPackageFragment) packageFragment).getChildren().length > 0;
		} catch (JavaModelException e) {
			return false;
		}
	}

}
