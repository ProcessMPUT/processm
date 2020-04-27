package processm.core.log.hierarchical

import processm.core.log.Classifier
import processm.core.log.Event
import processm.core.log.Extension
import processm.core.log.XESElement
import processm.core.log.attribute.*
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.querylanguage.Query
import java.sql.ResultSet
import java.util.*

/**
 * Reads a sequence of [Log]s from database, filtered by the given query. Every [Log] has a sequence
 * of traces associated in property [Log.traces], and every trace has a sequence of events associated
 * with property [Trace.events]. All sequences can be empty, but none of them will be null.
 * This implementation ensures certain guarantees:
 * * All sequences are lazy-evaluated and the references to [XESElement]s once yielded are cleared,
 * * Repeatable reads are ensured - each time this sequence is evaluated it yields exactly the same attribute values,
 * * Phantom reads are prevented - each time this sequence is evaluated it yields exactly the same [XESElement]s,
 * * The resulting view on [XESElement]s is read-only.
 *
 * @property query An instance of a PQL query.
 */
class DatabaseHierarchicalXESInputStream(val query: Query) : LogInputStream {
    companion object {
        private val logger = logger()
        private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

        /**
         * The maximum number of logs, traces, and events fetched at once. This value must be carefully chosen as
         * increasing it may substantially increase the memory consumptions. E.g., for [batchSize]=32, at most 32 logs
         * may be fetched at once, for each log at most 32 traces may be fetched, and for each trace at most 32 events
         * may be fetched, resulting in 32*32*32=32768 objects in cache. Assuming the average memory consumption for
         * each object is 128B (large collections of attributes may be expensive), then the log may consume
         * approximately 4MB.
         *
         * Note that the actual memory consumption may be lower, as it depends on the behavior of the consumer of this
         * [DatabaseHierarchicalXESInputStream] because the nested collections are fetched lazily.
         */
        private const val batchSize = 32
    }

    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use the primary constructor instead.", level = DeprecationLevel.WARNING)
    constructor(logId: Int) : this(Query(logId))

    private val translator = TranslatedQuery(query, batchSize)

    override fun iterator(): Iterator<Log> = getLogs().iterator()

    private fun getLogs(): Sequence<Log> = sequence {
        logger.enter()

        val executor = translator.getLogs()
        val buffer = ArrayDeque<Log>(batchSize)
        var hasNext = false
        do {
            // Execute log query
            executor.use {
                if (!it.hasNext())
                    return@use
                val r = it.next()
                while (r.entity.next())
                    buffer.addLast(readLog(r))
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            while (buffer.isNotEmpty()) {
                val log = buffer.pollFirst()!!
                logger.trace { "Yielding log: $log" }
                yield(log)
            }
        } while (hasNext)

        logger.exit()
    }

    private fun getTraces(logId: Int): Sequence<Trace> = sequence {
        logger.enter()

        val executor = translator.getTraces(logId)
        val buffer = ArrayDeque<Trace>(batchSize)
        var hasNext = false
        do {
            // Execute traces query
            executor.use {
                if (!it.hasNext())
                    return@use
                val r = it.next()
                while (r.entity.next())
                    buffer.addLast(readTrace(r, logId))
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            while (buffer.isNotEmpty()) {
                val trace = buffer.pollFirst()!!
                logger.trace { "Yielding trace: $trace" }
                yield(trace)
            }
        } while (hasNext)

        logger.exit()
    }

    private fun getEvents(logId: Int, traceId: Long): Sequence<Event> = sequence {
        logger.enter()

        val executor = translator.getEvents(logId, traceId)
        val buffer = ArrayDeque<Event>(batchSize)
        var hasNext = false
        do {
            // Execute events query
            executor.use {
                if (!it.hasNext())
                    return@use
                val r = it.next()
                while (r.entity.next())
                    buffer.addLast(readEvent(r))
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            while (buffer.isNotEmpty()) {
                val event = buffer.pollFirst()!!
                logger.trace { "Yielding event: $event" }
                yield(event)
            }
        } while (hasNext)

        logger.exit()
    }

    private fun readLog(result: LogQueryResult): Log {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Log()) {
            xesVersion = result.entity.getString("xes:version")
            xesFeatures = result.entity.getString("xes:features")
            conceptName = result.entity.getString("concept:name")
            identityId = result.entity.getString("identity:id")
            lifecycleModel = result.entity.getString("lifecycle:model")
            val logId = result.entity.getInt("id")

            // Load classifiers, extensions, globals and attributes inside log structure
            readClassifiers(result.classifiers, this, logId)
            readExtensions(result.extensions, this, logId)
            readGlobals(result.globals, this, logId)
            readLogAttributes(result.attributes, this, logId)

            // getTraces is a sequence, so it will be actually called when one reads it
            traces = getTraces(logId)

            logger.exit()
            return this
        }
    }

    private fun readClassifiers(resultSet: ResultSet?, log: Log, logId: Int) {
        resultSet ?: return

        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && resultSet.getInt("log_id") == logId) {
            with(resultSet) {
                val name = getString("name")
                val classifier = Classifier(name, getString("keys"))
                val scope = getString("scope")

                when (scope) {
                    "event" -> log.eventClassifiersInternal[name] = classifier
                    "trace" -> log.traceClassifiersInternal[name] = classifier
                    else -> throw IllegalStateException("Illegal scope $scope for the classifier.")
                }
                next()
            }
        }
    }

    private fun readExtensions(resultSet: ResultSet?, log: Log, logId: Int) {
        resultSet ?: return

        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && resultSet.getInt("log_id") == logId) {
            with(resultSet) {
                val prefix = getString("prefix")
                log.extensionsInternal[prefix] = Extension(getString("name"), prefix, getString("uri"))
                next()
            }
        }
    }

    private fun readGlobals(resultSet: ResultSet?, log: Log, logId: Int) {
        resultSet ?: return

        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && resultSet.getInt("log_id") == logId) {
            val scope = resultSet.getString("scope")
            val attribute = readRecordsIntoAttributes(resultSet)

            when (scope) {
                "event" -> log.eventGlobalsInternal[attribute.key] = attribute
                "trace" -> log.traceGlobalsInternal[attribute.key] = attribute
                else -> throw IllegalStateException("Illegal scope $scope for the global.")
            }
        }
    }

    private fun readTrace(result: QueryResult, logId: Int): Trace {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Trace()) {
            conceptName = result.entity.getString("concept:name")
            costCurrency = result.entity.getString("cost:currency")
            costTotal = result.entity.getDoubleOrNull("cost:total")
            identityId = result.entity.getString("identity:id")
            isEventStream = result.entity.getBoolean("event_stream")
            val traceId = result.entity.getLong("id")

            readTraceAttributes(result.attributes, this, traceId)

            // getEvents is a sequence, so it will be actually called when one reads it
            events = getEvents(logId, traceId)

            logger.exit()
            return this
        }
    }

    private fun readEvent(result: QueryResult): Event {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Event()) {
            conceptName = result.entity.getString("concept:name")
            conceptInstance = result.entity.getString("concept:instance")
            costTotal = result.entity.getDoubleOrNull("cost:total")
            costCurrency = result.entity.getString("cost:currency")
            identityId = result.entity.getString("identity:id")
            lifecycleState = result.entity.getString("lifecycle:state")
            lifecycleTransition = result.entity.getString("lifecycle:transition")
            orgRole = result.entity.getString("org:role")
            orgGroup = result.entity.getString("org:group")
            orgResource = result.entity.getString("org:resource")
            timeTimestamp = result.entity.getTimestamp("time:timestamp", gmtCalendar)?.toInstant()
            val eventId = result.entity.getLong("id")

            readEventAttributes(result.attributes, this, eventId)

            logger.exit()
            return this
        }
    }

    private fun readLogAttributes(resultSet: ResultSet, log: Log, logId: Int) =
        readAttributes(resultSet, { it.getInt("log_id") }, log, logId)

    private fun readTraceAttributes(resultSet: ResultSet, trace: Trace, traceId: Long): XESElement =
        readAttributes(resultSet, { it.getLong("trace_id") }, trace, traceId)

    private fun readEventAttributes(resultSet: ResultSet, event: Event, eventId: Long): XESElement =
        readAttributes(resultSet, { it.getLong("event_id") }, event, eventId)

    private fun readAttributes(
        resultSet: ResultSet,
        getElementId: (ResultSet) -> Number,
        element: XESElement,
        elementId: Number
    ): XESElement {
        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && getElementId(resultSet) == elementId) {
            with(readRecordsIntoAttributes(resultSet)) {
                element.attributesInternal[this.key] = this
            }
        }

        return element
    }

    private fun readRecordsIntoAttributes(resultSet: ResultSet): Attribute<*> {
        val attr = attributeFromRecord(resultSet.getString("key"), resultSet)
        val attrId = resultSet.getLong("id")

        if (!resultSet.next())
            return attr

        if (resultSet.getLong("parent_id") != attrId) {
            return attr
        } else {
            do {
                val isInsideList = resultSet.getBoolean("in_list_attr")
                with(readRecordsIntoAttributes(resultSet)) {
                    if (isInsideList) {
                        assert(attr is ListAttr)
                        (attr as ListAttr).valueInternal.add(this)
                    } else {
                        attr.childrenInternal[this.key] = this
                    }
                }
            } while (!resultSet.isEnded && resultSet.getLong("parent_id") == attrId)
        }

        return attr
    }

    private fun attributeFromRecord(key: String, record: ResultSet): Attribute<*> {
        with(record) {
            return when (getString("type")) {
                "int" -> IntAttr(key, getLong("int_value"))
                "id" -> IDAttr(key, getString("string_value"))
                "string" -> StringAttr(key, getString("string_value"))
                "bool" -> BoolAttr(key, getBoolean("bool_value"))
                "float" -> RealAttr(key, getDouble("real_value"))
                "date" -> DateTimeAttr(key, getTimestamp("date_value", gmtCalendar).toInstant())
                "list" -> ListAttr(key)
                else -> throw IllegalStateException("Invalid attribute type stored in the database")
            }
        }
    }

    private val ResultSet.isEnded
        // https://stackoverflow.com/a/15750832
        get() = this.isAfterLast || !this.isBeforeFirst && this.row == 0
}