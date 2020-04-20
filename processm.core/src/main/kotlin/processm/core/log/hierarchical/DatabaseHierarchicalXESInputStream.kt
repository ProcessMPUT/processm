package processm.core.log.hierarchical

import processm.core.log.Classifier
import processm.core.log.Event
import processm.core.log.Extension
import processm.core.log.XESElement
import processm.core.log.attribute.*
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.querylanguage.Query
import java.sql.ResultSet

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

    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use the primary constructor instead.", level = DeprecationLevel.WARNING)
    constructor(logId: Int) : this(Query(logId))

    private val logger = logger()
    private val translator = TranslatedQuery(query)

    override fun iterator(): Iterator<Log> = getLogs().iterator()

    private fun getLogs(): Sequence<Log> = sequence {
        logger.enter()

        val executor = translator.getLogs()
        var hasNext = false
        var log: Log?
        do {
            log = null
            // Execute log query
            executor.use {
                if (!it.hasNext())
                    return@use // in the first iteration we do not know if there is data
                log = readLog(it.next())
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            if (log === null)
                break
            logger.debug("Yielding log: $log")
            yield(log!!)
        } while (hasNext)

        logger.exit()
    }

    private fun getTraces(logId: Int): Sequence<Trace> = sequence {
        logger.enter()

        val executor = translator.getTraces(logId)
        var hasNext = false
        var trace: Trace?
        do {
            trace = null
            // Execute traces query
            executor.use {
                if (!it.hasNext())
                    return@use // in the first iteration we do not know if there is data
                trace = readTrace(it.next(), logId)
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            if (trace === null)
                break
            logger.debug("Yielding trace: $trace")
            yield(trace!!)
        } while (hasNext)

        logger.exit()
    }

    private fun getEvents(logId: Int, traceId: Long): Sequence<Event> = sequence {
        logger.enter()

        val executor = translator.getEvents(logId, traceId)
        var hasNext = false
        var event: Event?
        do {
            event = null
            // Execute events query
            executor.use {
                if (!it.hasNext())
                    return@use // in the first iteration we do not know if there is data
                event = readEvent(it.next())
                hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
            } // close()
            if (event === null)
                break
            logger.debug("Yielding event: $event")
            yield(event!!)
        } while (hasNext)

        logger.exit()
    }

    private fun readLog(result: LogQueryResult): Log {
        logger.enter()

        result.entity.next().let { assert(it) { "By contract exactly one row must exist." } }
        assert(result.entity.isLast) { "By contract exactly one row must exist." }

        with(Log()) {
            features = result.entity.getString("features")
            conceptName = result.entity.getString("concept:name")
            identityId = result.entity.getString("identity:id")
            lifecycleModel = result.entity.getString("lifecycle:model")
            val logId = result.entity.getInt("id")

            // Load classifiers, extensions, globals and attributes inside log structure
            readClassifiers(result.classifiers, this)
            readExtensions(result.extensions, this)
            readGlobals(result.globals, this)
            readLogAttributes(result.attributes, this)

            // getTraces is a sequence, so it will be actually called when one reads it
            traces = getTraces(logId)

            logger.exit()
            return this
        }
    }

    private fun readClassifiers(resultSet: ResultSet?, log: Log) {
        resultSet ?: return

        while (resultSet.next()) {
            with(resultSet) {
                val name = getString("name")
                val classifier = Classifier(name, keys = getString("keys"))

                when (getString("scope")) {
                    "event" ->
                        log.eventClassifiersInternal[name] = classifier
                    "trace" ->
                        log.traceClassifiersInternal[name] = classifier
                    else ->
                        throw IllegalStateException("Can not assign classifier with scope ${getString("scope")}")
                }
            }
        }
    }

    private fun readExtensions(resultSet: ResultSet?, log: Log) {
        resultSet ?: return

        while (resultSet.next()) {
            with(resultSet) {
                val prefix = getString("prefix")
                val extension = Extension(
                    name = getString("name"),
                    prefix = prefix,
                    uri = getString("uri")
                )

                log.extensionsInternal[prefix] = extension
            }
        }
    }

    private fun readGlobals(resultSet: ResultSet?, log: Log) {
        resultSet ?: return

        val fn = { r: ResultSet, k: String -> r.getInt(k) }
        if (!resultSet.next()) return

        while (!resultSet.isAfterLast) {
            val scope = resultSet.getString("scope")
            val attribute = readRecordsIntoAttributes(resultSet, fn)

            when (scope) {
                "event" ->
                    log.eventGlobalsInternal[attribute.key] = attribute
                "trace" ->
                    log.traceGlobalsInternal[attribute.key] = attribute
                else ->
                    throw IllegalStateException("Can not assign global attribute with scope $scope")
            }
        }
    }

    private fun readLogAttributes(resultSet: ResultSet, element: Log) =
        readAttributes(resultSet, { r: ResultSet, k: String -> r.getInt(k) }, element)

    private fun readTrace(result: QueryResult, logId: Int): Trace {
        logger.enter()

        result.entity.next().let { assert(it) { "By contract exactly one row must exist." } }
        assert(result.entity.isLast) { "By contract exactly one row must exist." }

        with(Trace()) {
            conceptName = result.entity.getString("concept:name")
            costCurrency = result.entity.getString("cost:currency")
            costTotal = result.entity.getDouble("cost:total")
            identityId = result.entity.getString("identity:id")
            isEventStream = result.entity.getBoolean("event_stream")
            val traceId = result.entity.getLong("id")

            readTracesEventsAttributes(result.attributes, this)

            // getEvents is a sequence, so it will be actually called when one reads it
            events = getEvents(logId, traceId)

            logger.exit()
            return this
        }
    }

    private fun readEvent(result: QueryResult): Event {
        logger.enter()

        result.entity.next().let { assert(it) { "By contract exactly one row must exist." } }
        assert(result.entity.isLast) { "By contract exactly one row must exist." }

        with(Event()) {
            conceptName = result.entity.getString("concept:name")
            conceptInstance = result.entity.getString("concept:instance")
            costTotal = result.entity.getDouble("cost:total")
            costCurrency = result.entity.getString("cost:currency")
            identityId = result.entity.getString("identity:id")
            lifecycleState = result.entity.getString("lifecycle:state")
            lifecycleTransition = result.entity.getString("lifecycle:transition")
            orgRole = result.entity.getString("org:role")
            orgGroup = result.entity.getString("org:group")
            orgResource = result.entity.getString("org:resource")
            timeTimestamp = result.entity.getTimestamp("time:timestamp")?.toInstant()
            // val eventId = result.entity.getLong("id")

            readTracesEventsAttributes(result.attributes, this)

            logger.exit()
            return this
        }
    }

    private fun readTracesEventsAttributes(resultSet: ResultSet, element: XESElement) =
        readAttributes(resultSet, { r: ResultSet, k: String -> r.getLong(k) }, element)

    private fun readAttributes(
        resultSet: ResultSet,
        fn: (ResultSet, String) -> Number,
        element: XESElement
    ): XESElement {
        if (!resultSet.next()) return element

        while (!resultSet.isAfterLast) {
            with(readRecordsIntoAttributes(resultSet, fn)) {
                element.attributesInternal[this.key] = this
            }
        }

        return element
    }

    private fun readRecordsIntoAttributes(resultSet: ResultSet, fn: (ResultSet, String) -> Number): Attribute<*> {
        val attr = attributeFromRecord(resultSet.getString("key"), resultSet)
        val attrId = fn(resultSet, "id")

        if (!resultSet.next()) return attr

        if (fn(resultSet, "parent_id") != attrId) {
            return attr
        } else {
            do {
                val isInsideList = resultSet.getBoolean("in_list_attr")
                with(readRecordsIntoAttributes(resultSet, fn)) {
                    if (isInsideList) {
                        assert(attr is ListAttr)
                        (attr as ListAttr).valueInternal.add(this)
                    } else {
                        attr.childrenInternal[this.key] = this
                    }
                }
            } while (!resultSet.isAfterLast && fn(resultSet, "parent_id") == attrId)
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
                "date" -> DateTimeAttr(key, getTimestamp("date_value").toInstant())
                "list" -> ListAttr(key)
                else -> throw IllegalStateException("Invalid attribute type stored in the database")
            }
        }
    }
}