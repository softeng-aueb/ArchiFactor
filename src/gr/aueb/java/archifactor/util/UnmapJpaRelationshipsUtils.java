package gr.aueb.java.archifactor.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
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
        String fullName = classObj.getName();
        if (fullName != null && fullName.contains(".")) {
            return fullName.substring(0, fullName.lastIndexOf("."));
        }
        return ""; // Default package
    }

    public static String determineServiceFactoryPackage(Set<String> servicesToCreate, Map<String, ClassObject> entityMap) {
        Set<String> packages = new HashSet<>();
        for (String entityName : servicesToCreate) {
            ClassObject entity = entityMap.get(entityName);
            if (entity != null) {
                packages.add(getPackageNameFromClass(entity));
            }
        }
        return findCommonAncestorPackage(packages);
    }

    private static String findCommonAncestorPackage(Set<String> packages) {
        Iterator<String> it = packages.iterator();
        String[] prefix = it.next().split("\\.");
        while (it.hasNext()) {
            String[] current = it.next().split("\\.");
            int minLength = Math.min(prefix.length, current.length);
            int i = 0;
            while (i < minLength && prefix[i].equals(current[i])) {
                i++;
            }
            prefix = Arrays.copyOf(prefix, i); // shrink the prefix
            if (prefix.length == 0) {
                return null; // no common ancestor
            }
        }
        return String.join(".", prefix);
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
    
    public static boolean isJpaRelationshipAnnotation(String annotationType) {
        return annotationType.equals("OneToMany") || 
               annotationType.equals("ManyToOne") || 
               annotationType.equals("OneToOne") || 
               annotationType.equals("ManyToMany");
    }
    
    public static boolean isJoinAnnotation(String annotationType) {
        return annotationType.equals("JoinColumn") || 
               annotationType.equals("JoinTable");
    }

    public static Type createAstTypeForFieldType(AST ast, String typeName) {
        switch (typeName.toLowerCase()) {
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