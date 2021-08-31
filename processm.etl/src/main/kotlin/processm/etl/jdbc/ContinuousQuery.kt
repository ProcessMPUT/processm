package processm.etl.jdbc

import processm.core.helpers.forceToUUID
import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.attribute.*
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.toMap
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.*

private const val IDENTITY_ID = "identity:id"
private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

/**
 * [SequenceWithConfirmation] provides a sequence with "transmission confirmation" - once an item is retrieved by next it is assumed
 * to be processed (or, at least - its ownership and responsibility for it were now transferred outside the sequence), and
 * a callback [itemConsumed] is called. [T] is the type for items in the underlying sequence, while [U] is an arbitrary type
 * of auxiliary data to be passed to the callback.
 */
class SequenceWithConfirmation<T, U>(
    private val base: Sequence<Pair<T, U>>,
    private val itemConsumed: (Pair<T, U>) -> Unit
) :
    Sequence<T> {

    private class IteratorWithConfirmation<T, U>(
        private val base: Iterator<Pair<T, U>>,
        private val itemConsumed: (Pair<T, U>) -> Unit
    ) :
        Iterator<T> {
        override fun hasNext(): Boolean = base.hasNext()

        override fun next(): T = base.next().also(itemConsumed).first

    }

    override fun iterator(): Iterator<T> = IteratorWithConfirmation(base.iterator(), itemConsumed)

}

/**
 * A special value for [ETLConfiguration.lastEventExternalId] to prevent [toXESInputStream] from binding it to a variable
 */
val ETLConfiguration.Companion.MAGIC_IGNORE_LAST_EVENT_EXTERNAL_ID: String
    get() = "ce69fe00-2728-4396-b371-f523ce188c#4775d4a312-cfcd-4d92-a38a-71e5ea36bebc"

/**
 * Produces an append-type stream of [XESComponent]s based on this [ETLConfiguration]. The append-type stream consists
 * of a partial event log with (possibly) partial traces. The partial traces are to be appended with previously seen
 * traces of the same id to produce (closer to) complete traces.
 *
 * The stream is organized hierarchically, i.e., it first produces a [Log], then [Trace]s interleaved by the [Event]s
 * belonging to the last produced [Trace]. Contrary to most implementations of the [XESInputStream], this one may
 * produce:
 * * Partial event logs and partial traces, however, the events are always complete.
 * * Traces split into parts and interleaved with the parts of other traces.
 *
 * The order of the events produces by this method corresponds to the order of the events yielded by the data source.
 *
 * Successive calls to this method produce incremental streams containing only the new [XESComponent]s. This method
 * updates [ETLConfiguration.lastEventExternalId] and the successive calls begin from the state, where the previous call
 * finished. It is the responsibility of the caller to commit the modified [ETLConfiguration]. Iterating concurrently
 * over two or more instances of this sequence for the same [ETLConfiguration] may yield unpredictable results.
 *
 * The [Log] produced by this sequence is guaranteed to contain the "identity:id" attribute that uniquely identifies
 * the log instance to append to if exists, or create a new one otherwise. The [Trace] produced by this sequence
 * consists of the attribute being the trace identifier in the source system. The name of this attribute is given in
 * [ETLColumnToAttributeMap.target] field for [ETLColumnToAttributeMap.traceId] equal true. The [Event] produced by
 * this sequence consists of all non-null attributes returned by the data source and mapped according to
 * [ETLColumnToAttributeMap].
 *
 * @throws java.sql.SQLException When an errors retrieving data from the data source occurs.
 * @throws IllegalArgumentException When the retrieved data does not contain required attributes, e.g., trace and event
 * ids.
 * @throws UnsupportedOperationException When the retrieved data has unsupported type.
 */
fun ETLConfiguration.toXESInputStream(): XESInputStream = SequenceWithConfirmation(sequence {
    DriverManager.getConnection(jdbcUri, user, password)
        .use { connection ->
            connection.prepareStatement(query).use { stmt ->
                // In theory it should be possible to retrieve the number of parameters via stmt.parameterMetaData.parameterCount - in practice it doesn't work on MSSQL.
                // Thus this ugly solution with ETLConfiguration.MAGIC_IGNORE_LAST_EVENT_EXTERNAL_ID - I believe this to be only necessary for testing
                if (lastEventExternalId != ETLConfiguration.MAGIC_IGNORE_LAST_EVENT_EXTERNAL_ID)
                    stmt.setObject(1, lastEventExternalId)

                stmt.executeQuery().use { rs ->
                    // helper structures
                    val columnMap = columnToAttributeMap.toMap()
                    val traceIdAttrDesc = columnToAttributeMap.first { it.traceId }
                    val eventIdAttrDesc = columnToAttributeMap.first { it.eventId }

                    // process rows
                    var lastLog: Log? = null
                    var lastTrace: Trace? = null
                    while (rs.next()) {
                        val attributes = toAttributes(rs, columnMap)
                        val traceId =
                            requireNotNull(attributes[traceIdAttrDesc.target]?.value) { "Trace id is not set in an event." }
                        val eventId =
                            requireNotNull(attributes[eventIdAttrDesc.target]?.value) { "Event id is not set in an event." }

                        // yield log if this is the first event
                        if (lastLog === null) {
                            lastLog = Log(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, logIdentityId)))
                            yield(lastLog to null)
                        }

                        // yield trace if changed
                        val traceIdentityId = traceId.forceToUUID()!!
                        if (lastTrace?.identityId != traceIdentityId) {
                            lastTrace = Trace(mutableMapOf(IDENTITY_ID to IDAttr(IDENTITY_ID, traceIdentityId)))
                            yield(lastTrace to null)
                        }

                        // yield event
                        attributes.computeIfAbsent(IDENTITY_ID) { IDAttr(IDENTITY_ID, eventId.forceToUUID()!!) }
                        yield(Event(attributes) to eventId)
                    }
                }
            }
        }
}) {
    if (it.first is Event) {
        val eventId = it.second
        checkNotNull(eventId)
        // lastEventExternalId must be updated after the event was consumed
        lastEventExternalId =
            if (EventIdCmp.compare(lastEventExternalId, eventId.toString()) >= 0) lastEventExternalId
            else eventId.toString()
    }
}

private fun toAttributes(rs: ResultSet, columnMap: Map<String, ETLColumnToAttributeMap>) =
    HashMap<String, Attribute<*>>().apply {
        val metadata = rs.metaData
        for (colIndex in 1..metadata.columnCount) {
            val colName = metadata.getColumnName(colIndex)
            val attrName = columnMap[colName]?.target ?: colName
            val attr = when (val colType = metadata.getColumnType(colIndex)) {
                Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                    rs.getString(colIndex)?.let { StringAttr(attrName, it) }
                Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
                    IntAttr(attrName, rs.getLong(colIndex))
                Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
                    RealAttr(attrName, rs.getDouble(colIndex))
                Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP, Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE ->
                    rs.getTimestamp(colIndex, gmtCalendar)?.let { DateTimeAttr(attrName, it.toInstant()) }
                Types.BIT, Types.BOOLEAN ->
                    BoolAttr(attrName, rs.getBoolean(colIndex))
                Types.NULL ->
                    NullAttr(attrName)
                Types.OTHER ->
                    IDAttr(attrName, rs.getObject(colIndex).forceToUUID()!!)
                else -> throw UnsupportedOperationException("Unsupported value type $colType for expression $colName.")
            }
            if (!rs.wasNull() && attr !== null)
                put(attrName, attr)
        }
    }

private object EventIdCmp : Comparator<String> {
    override fun compare(o1: String?, o2: String?): Int {
        if (o1 === null && o2 !== null)
            return -1
        else if (o1 !== null && o2 === null)
            return 1
        else if (o1 === null && o2 === null)
            return 0

        o1!!
        o2!!

        return try {
            o1.toLong().compareTo(o2.toLong())
        } catch (_: NumberFormatException) {
            try {
                o1.toUUID()!!.compareTo(o2.toUUID())
            } catch (_: IllegalArgumentException) {
                try {
                    o1.toDouble().compareTo(o2.toDouble())
                } catch (_: NumberFormatException) {
                    o1.compareTo(o2)
                }
            }
        }
    }

}
