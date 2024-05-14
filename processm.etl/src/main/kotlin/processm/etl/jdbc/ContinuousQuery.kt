package processm.etl.jdbc

import oracle.jdbc.OracleTypes
import processm.core.log.*
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.MutableAttributeMap
import processm.core.log.attribute.mutableAttributeMapOf
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.toMap
import processm.etl.helpers.getConnection
import processm.helpers.forceToUUID
import processm.helpers.toUUID
import processm.logging.logger
import java.sql.ResultSet
import java.sql.SQLFeatureNotSupportedException
import java.sql.Types
import java.time.Instant
import java.util.*
import java.util.concurrent.ForkJoinPool

/**
 * The timeout in milliseconds for the communication with the external DB. If no response is retrieved within this
 * value, an [java.sql.SQLException] will be thrown.
 */
private const val NETWORK_TIMEOUT: Int = 3 * 60 * 1000
private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

/**
 * [SequenceWithItemAcknowledgement] provides a sequence with "transmission confirmation" - once an item is retrieved by next it is assumed
 * to be processed (or, at least - its ownership and responsibility for it were now transferred outside the sequence), and
 * a callback [itemConsumed] is called. [T] is the type for items in the underlying sequence, while [U] is an arbitrary type
 * of auxiliary data to be passed to the callback.
 */
class SequenceWithItemAcknowledgement<T, U>(
    private val base: Sequence<Pair<T, U>>,
    private val itemConsumed: (Pair<T, U>) -> Unit
) :
    Sequence<T> {

    private class IteratorWithAcknowledgment<T, U>(
        private val base: Iterator<Pair<T, U>>,
        private val itemConsumed: (Pair<T, U>) -> Unit
    ) :
        Iterator<T> {
        override fun hasNext(): Boolean = base.hasNext()

        override fun next(): T = base.next().also(itemConsumed).first
    }

    override fun iterator(): Iterator<T> = IteratorWithAcknowledgment(base.iterator(), itemConsumed)
}

/**
 * [SequenceWithCompletionAcknowledgement] provides a sequence with "transmission confirmation" - once the whole sequence
 * is retrieved by next it is assumed to be processed (or, at least - its ownership and responsibility for it were now
 * transferred outside the sequence), and the callback [sequenceConsumed] is called, with the last item in the sequence
 * as the argument. [T] is the type for items in the underlying sequence, while [U] is an arbitrary type of auxiliary
 * data to be passed to the callback.
 */
class SequenceWithCompletionAcknowledgement<T, U>(
    private val base: Sequence<Pair<T, U>>,
    private val sequenceConsumed: (Pair<T, U>) -> Unit
) :
    Sequence<T> {

    private class IteratorWithAcknowledgment<T, U>(
        private val base: Iterator<Pair<T, U>>,
        private val sequenceConsumed: (Pair<T, U>) -> Unit
    ) :
        Iterator<T> {
        override fun hasNext(): Boolean = base.hasNext()

        override fun next(): T {
            val value = base.next()
            if (!base.hasNext())
                sequenceConsumed(value)
            return value.first
        }
    }

    override fun iterator(): Iterator<T> = IteratorWithAcknowledgment(base.iterator(), sequenceConsumed)
}

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
fun ETLConfiguration.toXESInputStream(): XESInputStream {
    if (batch && lastEventExternalId !== null)
        return emptySequence()
    val baseSequence = sequence<Pair<XESComponent, Any?>> {
        metadata.dataConnector.getConnection()
            .use { connection ->
                try {
                    connection.setNetworkTimeout(ForkJoinPool.commonPool(), NETWORK_TIMEOUT)
                } catch (e: SQLFeatureNotSupportedException) {
                    logger().error(e.message, e)
                }
                connection.prepareStatement(query).use { stmt ->
                    if (!batch)
                        stmt.setObject(1, castToSQLType(lastEventExternalId, lastEventExternalIdType))

                    metadata.lastExecutionTime = Instant.now()
                    stmt.executeQuery().use { rs ->
                        // helper structures
                        val columnMap = columnToAttributeMap.toMap()
                        val traceIdAttrDesc = columnToAttributeMap.first { it.traceId }
                        val eventIdAttrDesc = columnToAttributeMap.first { it.eventId }

                        if (lastEventExternalIdType === null)
                            lastEventExternalIdType = getAttributeType(rs, eventIdAttrDesc)

                        // process rows
                        var lastLog: Log? = null
                        var lastTrace: Trace? = null
                        while (rs.next()) {
                            val attributes = toAttributes(rs, columnMap)
                            val traceId =
                                requireNotNull(attributes.getOrNull(traceIdAttrDesc.target)) { "Trace id is not set in an event." }
                            val eventId =
                                requireNotNull(attributes.getOrNull(eventIdAttrDesc.target)) { "Event id is not set in an event." }

                            // yield log if this is the first event
                            if (lastLog === null) {
                                lastLog = Log(mutableAttributeMapOf(IDENTITY_ID to logIdentityId))
                                yield(lastLog to null)
                            }

                            // yield trace if changed
                            val traceIdentityId = traceId.forceToUUID()!!
                            if (lastTrace?.identityId != traceIdentityId) {
                                lastTrace = Trace(mutableAttributeMapOf(IDENTITY_ID to traceIdentityId))
                                yield(lastTrace to null)
                            }

                            // yield event
                            attributes.computeIfAbsent(IDENTITY_ID) { eventId.forceToUUID()!! }
                            yield(Event(attributes) to eventId)
                        }
                    }
                }
            }
    }

    fun ack(it: Pair<XESComponent, Any?>) {
        if (it.first is Event) {
            val eventId = it.second
            checkNotNull(eventId)
            // lastEventExternalId must be updated after the event was consumed
            lastEventExternalId =
                if (EventIdCmp(lastEventExternalIdType).compare(lastEventExternalId, eventId.toString()) >= 0)
                    lastEventExternalId
                else
                    eventId.toString()
        }
    }
    return if (batch)
        SequenceWithCompletionAcknowledgement(baseSequence, ::ack)
    else
        SequenceWithItemAcknowledgement(baseSequence, ::ack)
}

private fun getAttributeType(rs: ResultSet, attributeMap: ETLColumnToAttributeMap): Int? {
    val metadata = rs.metaData
    for (colIndex in 1..metadata.columnCount) {
        if (metadata.getColumnName(colIndex) == attributeMap.sourceColumn)
            return metadata.getColumnType(colIndex)
    }
    return null
}

private fun toAttributes(rs: ResultSet, columnMap: Map<String, ETLColumnToAttributeMap>) =
    MutableAttributeMap().apply {
        val metadata = rs.metaData
        for (colIndex in 1..metadata.columnCount) {
            val colName = metadata.getColumnName(colIndex)
            val attrName = columnMap[colName]?.target ?: colName
            val attr = when (val colType = metadata.getColumnType(colIndex)) {
                Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                    rs.getString(colIndex)

                Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
                    rs.getLong(colIndex)

                Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
                    rs.getDouble(colIndex)

                Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP, Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE, OracleTypes.TIMESTAMPLTZ ->
                    rs.getTimestamp(colIndex, gmtCalendar)?.toInstant()

                Types.BIT, Types.BOOLEAN ->
                    rs.getBoolean(colIndex)

                Types.NULL ->
                    null

                Types.OTHER ->
                    rs.getObject(colIndex).forceToUUID()

                else -> throw UnsupportedOperationException("Unsupported value type $colType for expression $colName.")
            }
            if (!rs.wasNull() && attr !== null)
                safeSet(attrName, attr)
        }
    }

private class EventIdCmp(private val type: Int?) : Comparator<String> {
    override fun compare(o1: String?, o2: String?): Int {
        if (o1 === null && o2 !== null)
            return -1
        else if (o1 !== null && o2 === null)
            return 1
        else if (o1 === null && o2 === null)
            return 0

        o1!!
        o2!!

        return when (type) {
            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
                o1.toLong().compareTo(o2.toLong())

            Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
                o1.toDouble().compareTo(o2.toDouble())

            null, Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                try {
                    o1.toUUID()!!.compareTo(o2.toUUID())
                } catch (_: IllegalArgumentException) {
                    o1.compareTo(o2)
                }

            else -> throw UnsupportedOperationException("Unsupported value type $type for expression eventId.")
        }
    }

}

private fun castToSQLType(value: String?, type: Int?): Any? {
    return when (type) {
        null, Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
            value

        Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
            value?.toLong()

        Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
            value?.toDouble()

        else -> throw UnsupportedOperationException("Unsupported value type $type for expression eventId.")
    }
}
