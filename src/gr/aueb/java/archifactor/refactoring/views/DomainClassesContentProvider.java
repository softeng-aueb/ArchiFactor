package gr.aueb.java.archifactor.refactoring.views;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import gr.aueb.java.archifactor.util.JavaModelUtil;

public class DomainClassesContentProvider extends StandardJavaElementContentProvider {
	
	
	RefactoringContext ctx = RefactoringContext.getInstance();

	@Override
	protected Object[] getPackageFragmentRoots(IJavaProject project) throws JavaModelException {
		return new Object[] {ctx.getSrcPackageFragmentRoot()};
	}
	
	@Override
	protected Object[] getPackageFragmentRootContent(IPackageFragmentRoot root) throws JavaModelException {
		Object [] packages = super.getPackageFragmentRootContent(root);
		
		return Arrays.asList(packages).stream()
				.filter(p -> JavaModelUtil.hasContent((IPackageFragment)p))
				.toArray();
	}
	
	@Override
	protected Object[] getPackageContent(IPackageFragment fragment) throws JavaModelException {
		// TODO filter domain classes here
		return super.getPackageContent(fragment);
	}
	

	@Override
	protected Object[] getJavaProjects(IJavaModel jm) throws JavaModelException {
		IJavaProject currentProject = RefactoringContext.getInstance().getTargetProject();
		IJavaProject[] allProjects = (IJavaProject[]) super.getJavaProjects(jm);
		return Arrays.asList(allProjects).stream().filter(p -> p.equals(currentProject)).collect(Collectors.toList())
				.toArray();

	}

}
