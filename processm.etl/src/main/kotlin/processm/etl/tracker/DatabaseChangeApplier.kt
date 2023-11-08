package processm.etl.tracker

import kotlinx.serialization.Serializable

interface DatabaseChangeApplier {

    /**
     * Method applying database change events to meta model.
     *
     * @param databaseChangeEvents List of database events to process.
     */
    fun applyChange(databaseChangeEvents: List<DatabaseChangeEvent>)

    @Serializable
    data class DatabaseChangeEvent(
        val entityKey: String,
        val entityId: String,
        val entityTable: String,
        val transactionId: String?,
        val timestamp: Long?,
        val eventType: EventType,
        val isSnapshot: Boolean,
        val objectData: Map<String, String>
    )

    enum class EventType {
        Unknown,
        Snapshot,
        Insert,
        Update,
        Delete
    }

    class UnsupportedEventFormat(message: String) : Exception(message)
}
