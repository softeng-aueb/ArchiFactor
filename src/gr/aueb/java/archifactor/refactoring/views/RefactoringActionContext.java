package gr.aueb.java.archifactor.refactoring.views;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.PackageFragment;

public class RefactoringActionContext {

	private static RefactoringActionContext context;
	private IJavaProject targetProject;
	private IContainer projectContainer;
	private IContainer rootPackage;
	private IContainer mavenSourceFolder;

	public static RefactoringActionContext getInstance() {
		if (context == null) {
			context = new RefactoringActionContext();
		}
		return context;
	}

	public void initialize(IJavaProject targetProject) {
		this.targetProject = targetProject;
		projectContainer = ResourcesPlugin.getWorkspace()
				.getRoot().getProject(targetProject.getPath().toString());
		IPackageFragmentRoot[] roots = null;
		try {
			roots = targetProject.getPackageFragmentRoots();
			IPackageFragmentRoot pkgRoot = roots[0];
			IJavaElement [] packageContent = pkgRoot.getChildren();
			for(IJavaElement element: packageContent) {
				if (element.getElementType() != IJavaElement.PACKAGE_FRAGMENT) {
					continue;
				}
				IPackageFragment pkg = (IPackageFragment) element;
				if (pkg.getChildren().length > 1) {
					// first package that contains java files
					IPath parentPath = pkg.getPath().removeLastSegments(1);
					rootPackage = projectContainer.getFolder(parentPath);
					break;
				}
			}
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mavenSourceFolder = projectContainer.getFolder(new Path("/src/main/java"));
		
	}


	public IContainer getProjectContainer() {
		return projectContainer;
	}
	
	public IContainer getMavenSourceFolder() {
		return mavenSourceFolder; 
	}
	
	public IContainer getRootPackage() {
		return rootPackage;
	}
}
