package processm.helpers;

import java.util.Arrays;
import java.util.Set;

public class ArrayComparison {
    /**
     * Compares for equality two arrays ignoring order of items.
     * For arrays of different length works in O(1) time and O(1) memory.
     * For arrays of the same length n works in O(nlog(n)) time and O(n) memory.
     *
     * @param array1 Must not contain duplicates
     * @param array2 Should not contain duplicates
     * @param <T>
     * @return
     */
    public static <T> boolean setEqual(T[] array1, T[] array2) {
        if (array1.length != array2.length)
            return false;
        if (array1.length == 0)
            return true;

        var set1 = Set.of(array1);
        for (var index = 0; index < array2.length; ++index) {
            if (!set1.contains(array2[index]))
                return false;
        }

        var set2 = Set.of(array2);
        return set1.equals(set2);
    }

    /**
     * Compares for equality two arrays ignoring order of items.
     * For arrays of different length works in O(1) time and O(1) memory.
     * For arrays of the same length n works in O(nlog(n)) time and O(1) memory.
     *
     * @param array1 Must not contain duplicates and be sorted
     * @param array2
     * @param <T>
     * @return
     */
    public static <T extends Comparable<T>> boolean sortedSetEqual(T[] array1, T[] array2) {
        if (array1.length != array2.length)
            return false;
        if (array1.length == 0)
            return true;

        assert isSorted(array1);
        for (var index = 0; index < array2.length; ++index) {
            if (Arrays.binarySearch(array1, array2[index]) < 0)
                return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> boolean isSorted(T[] array) {
        if (array.length <= 1)
            return true;

        var prev = array[0];
        for (int i = 1; i < array.length; i++) {
            var next = array[i];
            if (prev.compareTo(next) > 0)
                return false;
            prev = next;
        }

        return true;
    }
}
