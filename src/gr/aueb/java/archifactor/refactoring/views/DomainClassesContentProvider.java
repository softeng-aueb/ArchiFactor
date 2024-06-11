package gr.aueb.java.archifactor.refactoring.views;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

public class DomainClassesContentProvider extends StandardJavaElementContentProvider {

	@Override
	public Object[] getChildren(Object element) {

		if (element instanceof IJarEntryResource) {
			return NO_CHILDREN;
		}

		return super.getChildren(element);
	}

	@Override
	protected Object[] getJavaProjects(IJavaModel jm) throws JavaModelException {
		IJavaProject currentProject = RefactoringContext.getInstance().getTargetProject();
		IJavaProject[] allProjects = (IJavaProject[]) super.getJavaProjects(jm);
		return Arrays.asList(allProjects).stream().filter(p -> p.equals(currentProject)).collect(Collectors.toList())
				.toArray();

	}

}
