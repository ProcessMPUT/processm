package processm.core.log.hierarchical

import processm.core.log.*
import processm.core.log.attribute.*
import processm.core.persistence.DBConnectionPool
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
class DatabaseHierarchicalXESInputStream(private val logId: Int) : LogInputStream {
    /**
     * maxLogId, maxTraceId and maxEventId are collected when enumeration of this sequence (or any of its children)
     * finishes the first time. Successive enumerations use these values to prevent phantom reads by appending
     * queries with "WHERE log_id <= $maxLogId", "WHERE trace_id <= $maxTraceId" and "WHERE event_id <= $maxEventId",
     * respectively. This guarantees repeatable reads, assuming that the log is append-only.
     * */
    private var maxLogId: Int = -1
    private var maxTraceId: Long = -1L
    private var maxEventId: Long = -1L

    override fun iterator(): Iterator<Log> = getLogs(logId).map { it.second }.iterator()

    private fun getLogs(logId: Int): Sequence<Pair<Int, Log>> = sequence {
        var lastLogId: Int = -1
        var log: Pair<Int, Log>?
        while (true) {
            log = null
            openReadOnlyConnection().use { conn ->
                // Execute log query
                getLogStatement(conn, logId, lastLogId).executeQuery().use {
                    if (it.next()) {
                        assert(it.isLast)
                        log = parseLog(conn, it)
                        lastLogId = log!!.first
                    } else {
                        // No more logs. Eliminate phantom reads by getting and storing max trace_id and max event_id.
                        storeMaxIds(conn, logId)
                    }
                }
            } // close()
            if (log === null)
                break
            yield(log!!)
        }
    }

    private fun storeMaxIds(connection: Connection, logId: Int) {
        if (maxLogId >= 0 || maxTraceId >= 0L || maxEventId >= 0L)
            return
        connection.prepareStatement(
            """
            SELECT 
                MAX(l.id) AS max_log_id,
                MAX(t.id) AS max_trace_id,
                MAX(e.id) AS max_event_id
            FROM logs l 
            LEFT JOIN traces t ON l.id = t.log_id 
            LEFT JOIN events e ON t.id = e.trace_id
            WHERE l.id = ?
            """.trimIndent()
        ).apply {
            setInt(1, logId)
        }.executeQuery().use {
            it.next().let { success -> assert(success) }
            maxLogId = it.getInt("max_log_id")
            maxTraceId = it.getLong("max_trace_id")
            maxEventId = it.getLong("max_event_id")
        }
    }

    private fun getTraces(logId: Int): Sequence<Pair<Long, Trace>> = sequence {
        var lastTraceId: Long = -1L
        var trace: Pair<Long, Trace>?
        while (true) {
            trace = null
            openReadOnlyConnection().use { conn ->
                // Execute traces query
                getTracesStatement(conn, logId, lastTraceId).executeQuery().use {
                    if (it.next()) {
                        assert(it.isLast)
                        trace = parseTrace(conn, it)
                        lastTraceId = trace!!.first
                    } else {
                        // No more traces. Eliminate phantom reads by getting and storing max trace_id and max event_id.
                        storeMaxIds(conn, logId)
                    }
                }
            } // close()
            if (trace === null)
                break
            yield(trace!!)
        }
    }

    private fun getEvents(traceId: Long): Sequence<Event> = sequence {
        var lastEventId: Long = -1L
        var event: Pair<Long, Event>?
        while (true) {
            event = null
            openReadOnlyConnection().use { conn ->
                // Execute events query
                getEventsStatement(conn, traceId, lastEventId).executeQuery().use {
                    if (it.next()) {
                        assert(it.isLast)
                        event = parseEvent(conn, it)
                        lastEventId = event!!.first
                    } else {
                        // No more events. Eliminate phantom reads by getting and storing max trace_id and max event_id.
                        storeMaxIds(conn, logId)
                    }
                }
            } // close()
            if (event === null)
                break
            yield(event!!.second)
        }
    }

    private fun openReadOnlyConnection(): Connection =
        DBConnectionPool.getConnection().apply {
            autoCommit = false
            prepareStatement("START TRANSACTION READ ONLY").execute()
        }

    private fun getLogStatement(connection: Connection, logId: Int, lastLogId: Int): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                features,
                "concept:name",
                "identity:id",
                "lifecycle:model"
            FROM
                logs
            WHERE
                id = ?
                AND id > ?
                AND id <= ?
            LIMIT 1;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
            setInt(2, lastLogId)
            setInt(3, if (maxLogId <= 0) Int.MAX_VALUE else maxLogId)
        }

    private fun getClassifiersStatement(connection: Connection, logId: Int): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                scope,
                name,
                keys
            FROM
                classifiers
            WHERE
                log_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
        }

    private fun getExtensionsStatement(connection: Connection, logId: Int): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                name,
                prefix,
                uri
            FROM
                extensions
            WHERE
                log_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
        }

    private fun getGlobalsStatement(connection: Connection, logId: Int): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                parent_id,
                scope,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr 
            FROM
                globals
            WHERE
                log_id = ?
            ORDER BY id;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
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

    private fun getTracesStatement(connection: Connection, logId: Int, lastTraceId: Long): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                "concept:name",
                "cost:total",
                "cost:currency",
                "identity:id",
                event_stream
            FROM
                traces
            WHERE
                log_id = ?
                AND id > ?
                AND id <= ?
            ORDER BY id
            LIMIT 1;
            """.trimIndent()
        ).apply {
            setInt(1, logId)
            setLong(2, lastTraceId)
            setLong(3, if (maxTraceId <= 0L) Long.MAX_VALUE else maxTraceId)
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

    private fun getEventsStatement(connection: Connection, traceId: Long, lastEventId: Long): PreparedStatement =
        connection.prepareStatement(
            """
            SELECT
                id,
                "concept:name",
                "concept:instance",
                "cost:total",
                "cost:currency",
                "identity:id",
                "lifecycle:transition",
                "lifecycle:state",
                "org:resource",
                "org:role",
                "org:group",
                "time:timestamp"
            FROM
                events
            WHERE
                trace_id = ?
                AND id > ?
                AND id <= ?
            ORDER BY id
            LIMIT 1;
            """.trimIndent()
        ).apply {
            setLong(1, traceId)
            setLong(2, lastEventId)
            setLong(3, if (maxEventId <= 0L) Long.MAX_VALUE else maxEventId)
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

    private fun parseLog(connection: Connection, resultSet: ResultSet): Pair<Int, Log>? {
        with(Log()) {
            features = resultSet.getString("features")
            conceptName = resultSet.getString("concept:name")
            identityId = resultSet.getString("identity:id")
            lifecycleModel = resultSet.getString("lifecycle:model")
            val logId = resultSet.getInt("id")

            // Load classifiers, extensions, globals and attributes inside log structure
            parseClassifiers(getClassifiersStatement(connection, logId), this)
            parseExtensions(getExtensionsStatement(connection, logId), this)
            parseGlobals(getGlobalsStatement(connection, logId), this)
            parseLogAttributes(getLogAttributesStatement(connection, logId), this)

            traces = getTraces(logId).map { it.second }

            return Pair(logId, this)
        }
    }

    private fun parseClassifiers(query: PreparedStatement, log: Log): Log {
        val resultSet = query.executeQuery()
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

        return log
    }

    private fun parseExtensions(query: PreparedStatement, log: Log): Log {
        val resultSet = query.executeQuery()
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

        return log
    }

    private fun parseGlobals(query: PreparedStatement, log: Log): Log {
        val resultSet = query.executeQuery()
        val fn = { r: ResultSet, k: String -> r.getInt(k) }

        if (!resultSet.next()) return log

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

        return log
    }

    private fun parseLogAttributes(query: PreparedStatement, element: Log) =
        parseAttributes(query, { r: ResultSet, k: String -> r.getInt(k) }, element)

    private fun parseTrace(connection: Connection, resultSet: ResultSet): Pair<Long, Trace> {
        with(Trace()) {
            conceptName = resultSet.getString("concept:name")
            costCurrency = resultSet.getString("cost:currency")
            costTotal = resultSet.getDouble("cost:total")
            identityId = resultSet.getString("identity:id")
            isEventStream = resultSet.getBoolean("event_stream")
            val traceId = resultSet.getLong("id")

            parseTracesEventsAttributes(getTraceAttributesStatement(connection, traceId), this)
            events = getEvents(traceId)

            return Pair(traceId, this)
        }
    }

    private fun parseEvent(connection: Connection, resultSet: ResultSet): Pair<Long, Event> {
        with(Event()) {
            conceptName = resultSet.getString("concept:name")
            conceptInstance = resultSet.getString("concept:instance")
            costTotal = resultSet.getDouble("cost:total")
            costCurrency = resultSet.getString("cost:currency")
            identityId = resultSet.getString("identity:id")
            lifecycleState = resultSet.getString("lifecycle:state")
            lifecycleTransition = resultSet.getString("lifecycle:transition")
            orgRole = resultSet.getString("org:role")
            orgGroup = resultSet.getString("org:group")
            orgResource = resultSet.getString("org:resource")
            timeTimestamp = resultSet.getTimestamp("time:timestamp")
            val eventId = resultSet.getLong("id")

            parseTracesEventsAttributes(getEventAttributesStatement(connection, resultSet.getLong("id")), this)

            return Pair(eventId, this)
        }
    }

    private fun parseTracesEventsAttributes(query: PreparedStatement, element: XESElement) =
        parseAttributes(query, { r: ResultSet, k: String -> r.getLong(k) }, element)

    private fun parseAttributes(
        query: PreparedStatement,
        fn: (ResultSet, String) -> Number,
        element: XESElement
    ): XESElement {
        val resultSet = query.executeQuery()

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