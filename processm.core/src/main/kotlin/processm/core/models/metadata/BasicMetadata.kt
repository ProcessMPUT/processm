package processm.core.models.metadata

/**
 * Names for basic (well-known) statistics and metadata.
 */
object BasicMetadata {
    /**
     * The total wall-clock time from the very beginning of the business case to the very end of the business case.
     */
    val LEAD_TIME: URN = URN("urn:processm:statistics/lead_time")

    /**
     * The wall-clock time of actual work. May exceed [LEAD_TIME] if parallelism occurs in the process.
     */
    val SERVICE_TIME: URN = URN("urn:processm:statistics/service_time")

    /**
     * The wall-clock time of waiting for availability of resources.
     */
    val WAITING_TIME: URN = URN("urn:processm:statistics/waiting_time")

    /**
     * The wall-clock time of waiting for fully enabling a partially enabled activity, e.g., the time of waiting
     * for a missing token in a Petri net to enable a transition with two input places.
     */
    val SYNCHRONIZATION_TIME: URN = URN("urn:processm:statistics/synchronization_time")

    val SUSPENSION_TIME: URN = URN("urn:processm:statistics/suspension_time")

    /**
     * The set of [LEAD_TIME], [SERVICE_TIME], [WAITING_TIME], [SYNCHRONIZATION_TIME].
     */
    val BASIC_TIME_STATISTICS: Set<URN> = setOf(LEAD_TIME, SERVICE_TIME, WAITING_TIME, SYNCHRONIZATION_TIME)
}
