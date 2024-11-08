package processm.conformance.measures

import processm.core.models.metadata.URN

/**
 * A basic interface for a measure
 */
interface Measure<in T, out R> {
    /**
     * The urn to identify the measure
     */
    val URN: URN

    /**
     * Calculates the measure for the given [artifact].
     */
    operator fun invoke(artifact: T): R
}
