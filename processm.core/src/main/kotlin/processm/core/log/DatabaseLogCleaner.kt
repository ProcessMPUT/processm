package processm.core.log

import processm.core.persistence.DBConnectionPool
import java.sql.Connection

object DatabaseLogCleaner {
    fun removeLog(logId: Int) {
        DBConnectionPool.getConnection().use { connection ->
            connection.autoCommit = false

            removeLogRecord(connection, logId)
            removeClassifiers(connection, logId)
            removeExtensions(connection, logId)
            removeLogAttributes(connection, logId)
            removeGlobals(connection, logId)

            removeTraces(connection, logId)

            connection.commit()
        }
    }

    private fun removeTraces(connection: Connection, logId: Int) {
        val tracesIds = HashSet<Long>()
        with(connection.prepareStatement("""DELETE FROM traces WHERE log_id = ? RETURNING id""")) {
            setInt(1, logId)
            executeQuery().use { response ->
                while (response.next()) {
                    tracesIds.add(response.getLong("id"))
                }
            }
        }

        removeTracesAttributes(connection, tracesIds)

        for (traceId in tracesIds) {
            removeEvents(connection, traceId)
        }
    }

    private fun removeEvents(connection: Connection, traceId: Long) {
        val eventsIds = HashSet<Long>()
        with(connection.prepareStatement("""DELETE FROM events WHERE trace_id = ? RETURNING id""")) {
            setLong(1, traceId)
            executeQuery().use { response ->
                while (response.next()) {
                    eventsIds.add(response.getLong("id"))
                }
            }
        }

        removeEventAttributes(connection, eventsIds)
    }

    private fun removeEventAttributes(connection: Connection, eventsIds: HashSet<Long>) {
        with(connection.prepareStatement("""DELETE FROM events_attributes WHERE event_id = ANY (?)""")) {
            setArray(1, connection.createArrayOf("bigint", eventsIds.toArray()))
            execute()
        }
    }

    private fun removeTracesAttributes(connection: Connection, tracesIds: HashSet<Long>) {
        with(connection.prepareStatement("""DELETE FROM traces_attributes WHERE trace_id = ANY (?)""")) {
            setArray(1, connection.createArrayOf("bigint", tracesIds.toArray()))
            execute()
        }
    }

    private fun removeLogAttributes(connection: Connection, logId: Int) {
        with(connection.prepareStatement("""DELETE FROM logs_attributes WHERE log_id = ?""")) {
            setInt(1, logId)
            execute()
        }
    }

    private fun removeExtensions(connection: Connection, logId: Int) {
        with(connection.prepareStatement("""DELETE FROM extensions WHERE log_id = ?""")) {
            setInt(1, logId)
            execute()
        }
    }

    private fun removeClassifiers(connection: Connection, logId: Int) {
        with(connection.prepareStatement("""DELETE FROM classifiers WHERE log_id = ?""")) {
            setInt(1, logId)
            execute()
        }
    }

    private fun removeGlobals(connection: Connection, logId: Int) {
        with(connection.prepareStatement("""DELETE FROM globals WHERE log_id = ?""")) {
            setInt(1, logId)
            execute()
        }
    }

    private fun removeLogRecord(connection: Connection, logId: Int) {
        with(connection.prepareStatement("""DELETE FROM logs WHERE id = ?""")) {
            setInt(1, logId)
            execute()
        }
    }
}