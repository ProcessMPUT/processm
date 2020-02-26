package processm.core.models.metadata

/**
 * Names for basic (well-known) statistics, as defined in issue #11
 */
sealed class BasicStatistics() {
    companion object {
        val LEAD_TIME = URN("urn:processtom:statistics/lead_time")
        val SERVICE_TIME = URN("urn:processtom:statistics/service_time")
        val WAITING_TIME = URN("urn:processtom:statistics/waiting_time")
        val SYNCHRONIZATION_TIME = URN("urn:processtom:statistics/synchronization_time")
        val BASIC_TIME_STATISTICS = setOf(LEAD_TIME, SERVICE_TIME, WAITING_TIME, SYNCHRONIZATION_TIME)
    }

}