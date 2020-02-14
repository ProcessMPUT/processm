package processm.core.models.metadata

/**
 * Names for basic (well-known) statistics, as defined in issue #11
 */
sealed class BasicStatistics() {
    companion object {
        val LEAD_TIME = "Lead time"
        val SERVICE_TIME = "Service time"
        val WAITING_TIME = "Waiting time"
        val SYNCHRONIZATION_TIME = "Synchronization time"
        val BASIC_TIME_STATISTICS = listOf(LEAD_TIME, SERVICE_TIME, WAITING_TIME, SYNCHRONIZATION_TIME)
    }

}