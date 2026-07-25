package gr.aueb.java.archifactor.jpa.util;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;

import gr.aueb.java.archifactor.util.PackageUtils;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.SystemObject;

public class UnmapJpaRelationshipsUtils {
    public static String getSimpleTypeName(String fullyQualifiedType) {
        if (fullyQualifiedType.equals("int") || fullyQualifiedType.equals("long")) {
            return fullyQualifiedType;
        }

        int lastDot = fullyQualifiedType.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullyQualifiedType.substring(lastDot + 1);
        }
        return fullyQualifiedType;
    }

    public static String getSimpleClassName(String fullClassName) {
        if (fullClassName != null && fullClassName.contains(".")) {
            return fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        }
        return fullClassName;
    }

    public static String determineEntityPackage(SystemObject systemObject, String entityName) {
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObj = classIterator.next();
            if (entityName.equals(classObj.getName())) {
                return getPackageNameFromClass(classObj);
            }
        }
        return null;
    }

    public static String getPackageNameFromClass(ClassObject classObj) {
        return getPackageName(classObj.getName());
    }

    public static String getPackageName(String fullyQualifiedName) {
        if (fullyQualifiedName != null && fullyQualifiedName.contains(".")) {
            return fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf("."));
        }
        return ""; // Default package
    }

    public static String determineServiceFactoryPackage(Set<String> servicesToCreate, Map<String, ClassObject> entityMap) {
        Set<String> entityFqns = new HashSet<>();
        for (String entityName : servicesToCreate) {
            ClassObject entity = entityMap.get(entityName);
            if (entity != null) {
                entityFqns.add(entity.getName());
            }
        }
        return determineServiceFactoryPackage(entityFqns);
    }

    public static String determineServiceFactoryPackage(Set<String> entityFqns) {
        Set<String> packages = new HashSet<>();
        for (String entityFqn : entityFqns) {
            packages.add(getPackageName(entityFqn));
        }
        return PackageUtils.findCommonAncestorPackage(packages);
    }

    public static IPackageFragment findOrCreatePackage(IJavaProject project, String packageName) throws JavaModelException {
        IPackageFragmentRoot sourceFolder = null;
        for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
            if (root.getKind() == IPackageFragmentRoot.K_SOURCE && !root.isExternal()) {
                sourceFolder = root;
                break;
            }
        }
        
        if (sourceFolder == null) {
            return null;
        }

        IPackageFragment packageFragment = sourceFolder.getPackageFragment(packageName);
        if (!packageFragment.exists()) {
            packageFragment = sourceFolder.createPackageFragment(packageName, false, null);
        }
        return packageFragment;
    }
    

    public static Type createAstTypeForFieldType(AST ast, String typeName) {
        switch (typeName) {
            case "int":
                return ast.newPrimitiveType(PrimitiveType.INT);
            case "long":
                return ast.newPrimitiveType(PrimitiveType.LONG);
            default:
                return ast.newSimpleType(ast.newName(typeName));
        }
    }

    public static Expression createDefaultValueForPrimitiveType(AST ast, String typeName) {
        switch (typeName) {
            case "int":
                NumberLiteral zeroInt = ast.newNumberLiteral();
                zeroInt.setToken("0");
                return zeroInt;
            case "long":
                NumberLiteral zeroLong = ast.newNumberLiteral();
                zeroLong.setToken("0L");
                return zeroLong;
            default:
                return ast.newNullLiteral();
        }
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}