package gr.aueb.java.archifactor.refactoring.views;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import gr.aueb.java.util.Pair;

public class RefactoringContext {

	private static RefactoringContext context;
	private IJavaProject targetProject;
	private IContainer projectContainer;
//	private IContainer rootPackage;
	private List<IPackageFragment> rootPackages;
	private IContainer mavenSourceFolder;

	public static RefactoringContext getInstance() {
		if (context == null) {
			context = new RefactoringContext();
		}
		return context;
	}

	private IPackageFragmentRoot findSrcFolderRoot() throws JavaModelException {
		IPackageFragmentRoot[] rootFolders = targetProject.getPackageFragmentRoots();
		for (IPackageFragmentRoot rootFragment : rootFolders) {
			String fragmentName = rootFragment.getPath().toString();
			if (fragmentName.contains("/src")
					&& !(fragmentName.contains("/test") || fragmentName.contains("/resources"))) {
				return rootFragment;
			}
		}
		return null;
	}

	private boolean hasContent(IJavaElement packageFragment) {
		try {
			return ((IPackageFragment) packageFragment).getChildren().length > 0;
		} catch (JavaModelException e) {
			return false;
		}
	}

	private List<IPackageFragment> resolveRootPackage() {

		IPackageFragmentRoot srcFolder;
		try {
			srcFolder = findSrcFolderRoot();
			if (srcFolder == null) {
				return null;
			}

			IJavaElement[] packages = srcFolder.getChildren();

			Optional<Pair<Integer, List<PackagePathPair>>> minLengthPackageEntry = findMinLengthPackages(packages);

			if (minLengthPackageEntry.isEmpty())
				return null;

			List<PackagePathPair> minLengthPkgPaths = minLengthPackageEntry.get().second;

			if (minLengthPkgPaths.size() == 1) {
				return Arrays.asList(minLengthPkgPaths.get(0).first);
			}
			// else get parent paths and respective packages

			List<IPackageFragment> rootPackages = minLengthPkgPaths.stream().map(p -> p.second.removeLastSegments(1))
					.collect(Collectors.toSet()).stream()
					.map(p -> Arrays.asList(packages).stream().filter(pkg -> pkg.getPath().equals(p)).findFirst())
					.filter(opt -> opt.isPresent()).map(opt -> (IPackageFragment) opt.get())
					.collect(Collectors.toList());

			return rootPackages;

		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private Optional<Pair<Integer, List<PackagePathPair>>> findMinLengthPackages(IJavaElement[] packages) {
		
		Stream<IJavaElement> pkgStream = Arrays.asList(packages).stream();
		Optional<Pair<Integer, List<PackagePathPair>>> minLengthPackageEntry = pkgStream
				.filter(p -> p.getElementType() == IJavaElement.PACKAGE_FRAGMENT).filter(p -> hasContent(p))
				// keep non empty packages (contain Java files)
				.map(p -> (IPackageFragment) p).map(p -> new PackagePathPair(p, p.getPath()))
				// group packages by path segment length
				.collect(Collectors.groupingBy(new Function<PackagePathPair, Integer>() {
					@Override
					public Integer apply(PackagePathPair t) {
						return t.second.segmentCount();
					}
				})).entrySet().stream()
				// map entries to simple pair objects
				.map(entry -> new Pair<Integer, List<PackagePathPair>>(entry.getKey(), entry.getValue()))
				// choose packages with min path length
				.collect(Collectors.minBy(new Comparator<Pair<Integer, List<PackagePathPair>>>() {
					@Override
					public int compare(Pair<Integer, List<PackagePathPair>> o1,
							Pair<Integer, List<PackagePathPair>> o2) {
						return o1.first - o2.first;
					}
				}));
		return minLengthPackageEntry;
	}

	public void initialize(IJavaProject targetProject) {
		this.targetProject = targetProject;
		projectContainer = ResourcesPlugin.getWorkspace().getRoot().getProject(targetProject.getPath().toString());
		rootPackages = resolveRootPackage();
//		rootPackage = projectContainer.getFolder(rootPackages.getPath());
		mavenSourceFolder = projectContainer.getFolder(new Path("/src/main/java"));

	}

	public IContainer getProjectContainer() {
		return projectContainer;
	}

	public IContainer getMavenSourceFolder() {
		return mavenSourceFolder;
	}

	public List<IPackageFragment> getRootPackages() {
		return rootPackages;
	}

	public IJavaProject getTargetProject() {
		return targetProject;
	}
}
