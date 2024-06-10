package processm.helpers;

import java.util.Set;

public class ImmutableSet {
    /**
     * This is delegate to {@link Set#of(Object)} for Kotlin interop.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> Set<E> of(E... elements) {
        return Set.of(elements);
    }
}
