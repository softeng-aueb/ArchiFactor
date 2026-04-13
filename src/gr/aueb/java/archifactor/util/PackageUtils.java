package gr.aueb.java.archifactor.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class PackageUtils {

    public static String findCommonAncestorPackage(Set<String> packages) {
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
}
