package processm.core.log

import java.sql.Connection

object DBLogCleaner {
    fun removeLog(connection: Connection, logId: Int) {
        connection.autoCommit = false

        removeLogRecord(connection, logId)
        removeTraces(connection, logId)

        connection.commit()
    }

    private fun removeTraces(connection: Connection, logId: Int) {
        val tracesIds = HashSet<Long>()
        connection.prepareStatement("""DELETE FROM traces WHERE log_id = ? RETURNING id""").use {
            it.setInt(1, logId)
            it.executeQuery().use { response ->
                while (response.next()) {
                    tracesIds.add(response.getLong("id"))
                }
            }
        }

        removeTracesAttributes(connection, tracesIds)
        removeEvents(connection, tracesIds)
    }

    private fun removeEvents(connection: Connection, traceIds: Collection<Long>) {
        val eventsIds = HashSet<Long>()
        connection.prepareStatement("""DELETE FROM events WHERE trace_id = ANY(?) RETURNING id""").use {
            it.setArray(1, connection.createArrayOf("bigint", traceIds.toTypedArray()))
            it.executeQuery().use { response ->
                while (response.next()) {
                    eventsIds.add(response.getLong("id"))
                }
            }
        }

        removeEventAttributes(connection, eventsIds)
    }

    private fun removeEventAttributes(connection: Connection, eventsIds: HashSet<Long>) {
        connection.prepareStatement("""DELETE FROM events_attributes WHERE event_id = ANY (?)""").use {
            it.setArray(1, connection.createArrayOf("bigint", eventsIds.toArray()))
            it.execute()
        }
    }

    private fun removeTracesAttributes(connection: Connection, tracesIds: HashSet<Long>) {
        connection.prepareStatement("""DELETE FROM traces_attributes WHERE trace_id = ANY (?)""").use {
            it.setArray(1, connection.createArrayOf("bigint", tracesIds.toArray()))
            it.execute()
        }
    }

    private fun removeLogRecord(connection: Connection, logId: Int) {
        connection.prepareStatement(
            """DELETE FROM logs WHERE id=?;
            |DELETE FROM globals WHERE log_id=?; 
            |DELETE FROM classifiers WHERE log_id=?; 
            |DELETE FROM extensions WHERE log_id=?; 
            |DELETE FROM logs_attributes WHERE log_id=?""".trimMargin()
        ).use {
            it.setInt(1, logId)
            it.setInt(2, logId)
            it.setInt(3, logId)
            it.setInt(4, logId)
            it.setInt(5, logId)
            it.execute()
        }
    }
}
