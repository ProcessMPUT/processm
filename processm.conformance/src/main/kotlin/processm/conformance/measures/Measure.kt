package processm.conformance.measures

/**
 * A basic interface for a measure
 */
fun interface Measure<T, out R> {
    operator fun invoke(artifact: T): R
}