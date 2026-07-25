package gr.aueb.java.archifactor.jpa.util;

import java.util.Collection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;

import gr.aueb.java.archifactor.jpa.enums.PersistenceNamespace;
import gr.uom.java.ast.ClassObject;

/**
 * Determines which persistence namespace a project is mapped with by reading the
 * imports the entities themselves declare.
 */
public class PersistenceNamespaceDetector {

    public static PersistenceNamespace detect(Collection<ClassObject> entities) {
        int javax = count(PersistenceNamespace.JAVAX, entities);
        int jakarta = count(PersistenceNamespace.JAKARTA, entities);
        return javax > jakarta ? PersistenceNamespace.JAVAX : PersistenceNamespace.JAKARTA;
    }

    public static boolean isMixed(Collection<ClassObject> entities) {
        return count(PersistenceNamespace.JAVAX, entities) > 0 && count(PersistenceNamespace.JAKARTA, entities) > 0;
    }

    private static int count(PersistenceNamespace namespace, Collection<ClassObject> entities) {
        String importPrefix = namespace.type("persistence.");

        int matches = 0;
        for (ClassObject entity : entities) {
            if (declaresImportStartingWith(entity, importPrefix)) {
                matches++;
            }
        }
        return matches;
    }

    private static boolean declaresImportStartingWith(ClassObject entity, String importPrefix) {
        ITypeRoot typeRoot = entity.getITypeRoot();
        if (!(typeRoot instanceof ICompilationUnit)) {
            return false;
        }

        try {
            for (IImportDeclaration importDeclaration : ((ICompilationUnit) typeRoot).getImports()) {
                if (importDeclaration.getElementName().startsWith(importPrefix)) {
                    return true;
                }
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }

        return false;
    }
}
