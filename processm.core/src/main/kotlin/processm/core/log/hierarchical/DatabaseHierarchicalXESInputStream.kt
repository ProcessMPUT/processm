package processm.core.log.hierarchical

import processm.core.log.Classifier
import processm.core.log.Event
import processm.core.log.Extension
import processm.core.log.XESElement
import processm.core.log.attribute.*
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.Query
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Reads a sequence of [Log]s from database, filtered by the given query. Every [Log] has a sequence
 * of traces associated in property [Log.traces], and every trace has a sequence of events associated
 * with property [Trace.events]. All sequences can be empty, but none of them will be null.
 * This implementation ensures certain guarantees:
 * * All sequences are lazy-evaluated and the references to [XESElement]s are cleared after yielded,
 * * Repeatable reads are ensured - each time this sequence is evaluated it yields exactly the same attribute values,
 * * Phantom reads are prevented - each time this sequence is evaluated it yields exactly the same [XESElement]s,
 * * The resulting view on [XESElement]s is read-only.
 */
class DatabaseHierarchicalXESInputStream(val query: Query) : LogInputStream {

    /**
     * This constructor is provided for backward-compatibility with the previous implementation of XES layer and its
     * use is discouraged in new code.
     * @param logId is the database id of the log. Not to be confused with log:identity:id.
     */
    @Deprecated("Use the primary constructor instead.", level = DeprecationLevel.WARNING)
    constructor(logId: Int) : this(Query(logId))

    private val translator = TranslatedQuery(query)

    override fun iterator(): Iterator<Log> = getLogs().map { it.second }.iterator()

    private fun getLogs(): Sequence<Pair<Int, Log>> = sequence {
        val executor = translator.getLogs()
        var hasNext: Boolean = false
        var log: Pair<Int, Log>?
        do {
            log = null
            openReadOnlyConnection().use { conn ->
                // Execute log query
                executor.use(conn) {
                    if (!it.hasNext())
                        return@use // in the first iteration we do not know if there is data
                    log = parseLog(it.next())
                    hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
                }
            } // close()
            if (log === null)
                break
            yield(log!!)
        } while (hasNext)
    }

    private fun getTraces(logId: Int): Sequence<Pair<Long, Trace>> = sequence {
        val executor = translator.getTraces(logId)
        var hasNext = false
        var trace: Pair<Long, Trace>?
        do {
            trace = null
            openReadOnlyConnection().use { conn ->
                // Execute traces query
                executor.use(conn) {
                    if (!it.hasNext())
                        return@use // in the first iteration we do not know if there is data
                    trace = parseTrace(it.next(), logId)
                    hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
                }
            } // close()
            if (trace === null)
                break
            yield(trace!!)
        } while (hasNext)
    }

    private fun getEvents(logId: Int, traceId: Long): Sequence<Event> = sequence {
        val executor = translator.getEvents(logId, traceId)
        var hasNext = false
        var event: Pair<Long, Event>?
        do {
            event = null
            openReadOnlyConnection().use { conn ->
                // Execute events query
                executor.use(conn) {
                    if (!it.hasNext())
                        return@use // in the first iteration we do not know if there is data
                    event = parseEvent(it.next())
                    hasNext = it.hasNext() // prevent reconnecting in the next iteration if there is no more data
                }
            } // close()
            if (event === null)
                break
            yield(event!!.second)
        } while (hasNext)
    }

    private fun openReadOnlyConnection(): Connection =
        DBConnectionPool.getConnection().apply {
            autoCommit = false
            prepareStatement("START TRANSACTION READ ONLY").execute()
        }

    private fun getLogAttributesStatement(connection: Connection, logId: Int): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                parent_id,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr 
            FROM
                logs_attributes
            WHERE
                log_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
        }

    private fun getTraceAttributesStatement(connection: Connection, traceId: Long): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                parent_id,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr 
            FROM
                traces_attributes
            WHERE
                trace_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setLong(1, traceId)
        }

    private fun getEventAttributesStatement(connection: Connection, eventId: Long): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                parent_id,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr 
            FROM
                events_attributes
            WHERE
                event_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setLong(1, eventId)
        }

    private fun parseLog(result: LogQueryResult): Pair<Int, Log>? {
        result.entity.next().let { assert(it) { "By contract exactly one row must exist." } }
        assert(result.entity.isLast) { "By contract exactly one row must exist." }

        with(Log()) {
            features = result.entity.getString("features")
            conceptName = result.entity.getString("concept:name")
            identityId = result.entity.getString("identity:id")
            lifecycleModel = result.entity.getString("lifecycle:model")
            val logId = result.entity.getInt("id")

            // Load classifiers, extensions, globals and attributes inside log structure
            parseClassifiers(result.classifiers, this)
            parseExtensions(result.extensions, this)
            parseGlobals(result.globals, this)
            parseLogAttributes(result.attributes, this)

            traces = getTraces(logId).map { it.second }

            return Pair(logId, this)
        }
    }

    private fun parseClassifiers(resultSet: ResultSet?, log: Log) {
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

    private fun parseExtensions(resultSet: ResultSet?, log: Log) {
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

    private fun parseGlobals(resultSet: ResultSet?, log: Log) {
        resultSet ?: return

        val fn = { r: ResultSet, k: String -> r.getInt(k) }
        if (!resultSet.next()) return

        while (!resultSet.isAfterLast) {
            val scope = resultSet.getString("scope")
            val attribute = parseRecordsIntoAttributes(resultSet, fn)

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

    private fun parseLogAttributes(resultSet: ResultSet, element: Log) =
        parseAttributes(resultSet, { r: ResultSet, k: String -> r.getInt(k) }, element)

    private fun parseTrace(result: QueryResult, logId: Int): Pair<Long, Trace> {
        result.entity.next().let { assert(it) { "By contract exactly one row must exist." } }
        assert(result.entity.isLast) { "By contract exactly one row must exist." }

        with(Trace()) {
            conceptName = result.entity.getString("concept:name")
            costCurrency = result.entity.getString("cost:currency")
            costTotal = result.entity.getDouble("cost:total")
            identityId = result.entity.getString("identity:id")
            isEventStream = result.entity.getBoolean("event_stream")
            val traceId = result.entity.getLong("id")

            parseTracesEventsAttributes(result.attributes, this)
            events = getEvents(logId, traceId)

            return Pair(traceId, this)
        }
    }

    private fun parseEvent(result: QueryResult): Pair<Long, Event> {
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
            timeTimestamp = result.entity.getTimestamp("time:timestamp")
            val eventId = result.entity.getLong("id")

            parseTracesEventsAttributes(result.attributes, this)

            return Pair(eventId, this)
        }
    }

    private fun parseTracesEventsAttributes(resultSet: ResultSet, element: XESElement) =
        parseAttributes(resultSet, { r: ResultSet, k: String -> r.getLong(k) }, element)

    private fun parseAttributes(
        resultSet: ResultSet,
        fn: (ResultSet, String) -> Number,
        element: XESElement
    ): XESElement {
        if (!resultSet.next()) return element

        while (!resultSet.isAfterLast) {
            with(parseRecordsIntoAttributes(resultSet, fn)) {
                element.attributesInternal[this.key] = this
            }
        }

        return element
    }

    private fun parseRecordsIntoAttributes(resultSet: ResultSet, fn: (ResultSet, String) -> Number): Attribute<*> {
        val attr = attributeFromRecord(resultSet.getString("key"), resultSet)
        val attrId = fn(resultSet, "id")

        if (!resultSet.next()) return attr

        if (fn(resultSet, "parent_id") != attrId) {
            return attr
        } else {
            do {
                val isInsideList = resultSet.getBoolean("in_list_attr")
                with(parseRecordsIntoAttributes(resultSet, fn)) {
                    if (isInsideList) {
                        assert(attr is ListAttr)
                        (attr as ListAttr).valueInternal.add(this)
                    } else {
                        attr.childrenInternal[this.key] = this
                    }
                }
            } while (fn(resultSet, "parent_id") == attrId)
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
                "date" -> DateTimeAttr(key, getTimestamp("date_value"))
                "list" -> ListAttr(key)
                else -> throw IllegalStateException("Invalid attribute type stored in the database")
            }
        }
    }
}