package processm.conformance.measures

/**
 * A basic interface for a measure
 */
fun interface Measure<T, out R> {
    /**
     * Calculates the measure for the given [artifact].
     */
    operator fun invoke(artifact: T): R
}
