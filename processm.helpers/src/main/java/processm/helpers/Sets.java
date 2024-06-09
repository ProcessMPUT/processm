package processm.helpers;

import java.util.Set;

public class Sets {
    /**
     * Verifies for equality two arrays ignoring order of items.
     *
     * @param array1
     * @param array2
     * @param <T>
     * @return
     */
    public static <T> boolean equal(T[] array1, T[] array2) {
        if (array1.length != array2.length) {
            return false;
        }
        var set1 = Set.of(array1);
        var set2 = Set.of(array2);
        return set1.equals(set2);
    }
}
