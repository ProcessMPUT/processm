package processm.core.log

import java.sql.Connection
import java.util.*

/**
 * An output XES stream that appends traces/events to the existing event log. The event log must have the identity:id
 * attribute set for identification. To make appending to the existing traces possible, the traces must also have
 * the identity:id attribute set. The anonymous traces with identity:id unset will not be appended and instead a new
 * trace will be created.
 */
class AppendingDBXESOutputStream(connection: Connection, batchSize: Int = DBXESOutputStream.batchSize) :
    DBXESOutputStream(connection, true, batchSize) {
    override fun write(component: XESComponent) {
        if (component is Log) {
            val existingLogId = getLogId(component)
            if (existingLogId !== null) {
                if (existingLogId != this.logId) {
                    flushQueue(true) // flush events and traces from the previous log
                    sawTrace = false // we must not refer to a trace from the previous log
                    this.logId = existingLogId
                }
                //Don't write the component if this log is already known
                //FIXME Possibly this leads to data loss if `component` contains new values for some attributes of the log
            } else {
                super.write(component)
            }
        } else {
            super.write(component)
        }
    }

    private fun getLogId(log: Log): Int? {
        requireNotNull(log.identityId) { "The attribute identity:id must be set for the log." }
        connection.prepareStatement("SELECT id FROM logs WHERE \"identity:id\"=?::uuid").use { stmt ->
            stmt.setString(1, log.identityId.toString())
            val rs = stmt.executeQuery()
            if (rs.next())
                return rs.getInt(1)
            return null
        }
    }

    private fun rearrangeQueue() {
        val traces = LinkedHashMap<UUID, Pair<Trace, ArrayList<Event>>>()
        var last: Pair<Trace, ArrayList<Event>>? = null

        fun getRandomUUID(): UUID {
            var uuid: UUID
            do {
                uuid = UUID.randomUUID()
            } while (uuid in traces)
            return uuid
        }

        for (component in queue) {
            when (component) {
                is Event -> last!!.second.add(component)
                is Trace -> {
                    val identityId = component.identityId ?: getRandomUUID()
                    last = traces.computeIfAbsent(identityId) {
                        component to ArrayList<Event>()
                    }
                }
            }
        }

        queue.clear()
        for ((trace, events) in traces.values) {
            queue.add(trace)
            queue.addAll(events)
        }
    }

    override fun flushQueue(force: Boolean) {
        if (queue.isEmpty())
            return

        rearrangeQueue()
        super.flushQueue(force)
    }
}
