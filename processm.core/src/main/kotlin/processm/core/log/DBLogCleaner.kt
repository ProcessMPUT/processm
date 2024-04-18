package processm.core.log

import java.sql.Connection
import java.util.*

object DBLogCleaner {
    /**
     * Removes the log with the given database id and the contained traces and events.
     * The connection and transaction management is the responsibility of the caller of this method.
     * @param connection Database connection
     * @param logId The database id of the log
     */
    fun removeLog(connection: Connection, logId: Int) = removeLogs(connection, listOf(logId))

    fun removeLog(connection: Connection, identityId: UUID): Boolean {
        val logId = with(connection.prepareStatement("""SELECT id FROM logs WHERE "identity:id" = ?""")) {
            setObject(1, identityId)
            executeQuery().use { response ->
                return@with if (response.next()) response.getInt("id") else null
            }
        } ?: return false

        removeLog(connection, logId)
        return true
    }

    /**
     * Removes the logs with the given database ids and the contained traces and events.
     * The connection and transaction management is the responsibility of the caller of this method.
     * @param connection Database connection
     * @param logIds The collection of database ids of the logs
     */
    internal fun removeLogs(connection: Connection, logIds: Collection<Int>) {
        removeLogRecord(connection, logIds)
        removeTracesOfLogs(connection, logIds)
    }

    private fun removeTracesOfLogs(connection: Connection, logIds: Collection<Int>) {
        connection.prepareStatement(
            """WITH deleted_traces AS (DELETE FROM traces WHERE log_id=ANY(?) RETURNING id),
                deleted_events AS (DELETE FROM events WHERE trace_id=ANY(SELECT id FROM deleted_traces) RETURNING id),
                ignore AS (DELETE FROM events_attributes WHERE event_id = ANY (SELECT id FROM deleted_events))
                DELETE FROM traces_attributes WHERE trace_id = ANY (SELECT id FROM deleted_traces)
            """
        ).use {
            it.setArray(1, connection.createArrayOf("int", logIds.toTypedArray()))

            it.execute()
        }
    }

    /**
     * Removes the traces with the given database ids and the contained events.
     * The connection and transaction management is the responsibility of the caller of this method.
     * @param connection Database connection
     * @param traceIds The collection of trace ids. For null all traces of the given log are removed.
     */
    internal fun removeTraces(connection: Connection, traceIds: Collection<Long>) {
        connection.prepareStatement(
            """DELETE FROM traces WHERE id=ANY(?);
                DELETE FROM traces_attributes WHERE trace_id=ANY(?);
                WITH deleted_events AS (DELETE FROM events WHERE trace_id=ANY(?) RETURNING id)
                DELETE FROM events_attributes WHERE event_id = ANY(SELECT id FROM deleted_events)
            """.trimMargin()
        ).use {
            val array = connection.createArrayOf("bigint", traceIds.toTypedArray())
            it.setArray(1, array)
            it.setArray(2, array)
            it.setArray(3, array)

            it.execute()
        }
    }

    /**
     * Removes the events based on their database ids.
     * The connection and transaction management is the responsibility of the caller of this method.
     * @param connection Database connection
     * @param eventIds The collection of event ids. For null all events of the given traces are removed.
     */
    internal fun removeEvents(connection: Connection, eventIds: Collection<Long>) {
        connection.prepareStatement(
            "DELETE FROM events WHERE id=ANY(?); DELETE FROM events_attributes WHERE event_id=ANY(?)"
        ).use {
            val array = connection.createArrayOf("bigint", eventIds.toTypedArray())
            it.setArray(1, array)
            it.setArray(2, array)

            it.execute()
        }
    }

    private fun removeLogRecord(connection: Connection, logIds: Collection<Int>) {
        connection.prepareStatement(
            """DELETE FROM logs WHERE id=ANY(?);
            |DELETE FROM globals WHERE log_id=ANY(?); 
            |DELETE FROM classifiers WHERE log_id=ANY(?); 
            |DELETE FROM extensions WHERE log_id=ANY(?); 
            |DELETE FROM logs_attributes WHERE log_id=ANY(?)""".trimMargin()
        ).use {
            val array = connection.createArrayOf("int", logIds.toTypedArray())
            it.setArray(1, array)
            it.setArray(2, array)
            it.setArray(3, array)
            it.setArray(4, array)
            it.setArray(5, array)
            it.execute()
        }
    }
}
