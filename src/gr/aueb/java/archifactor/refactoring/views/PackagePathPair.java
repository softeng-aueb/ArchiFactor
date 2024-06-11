package gr.aueb.java.archifactor.refactoring.views;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IPackageFragment;

import gr.aueb.java.util.Pair;

public class PackagePathPair extends Pair<IPackageFragment, IPath>{

	public PackagePathPair(IPackageFragment first, IPath second) {
		super(first, second);
	}

}
