package processm.core.log

import processm.core.log.attribute.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.*

class DBXESOutputStream(private val connection: Connection) : XESOutputStream {
    companion object {
        private const val batchSize = 32
    }

    /**
     * Log ID of inserted Log record
     */
    private var logId: Int? = null

    /**
     * Trace ID of inserted Trace record
     */
    private var traceId: Long? = null

    private val eventQueue = ArrayList<Event>(batchSize)

    init {
        assert(connection.metaData.supportsGetGeneratedKeys())
        assert(connection.metaData.supportsTransactions())
        assert(connection.metaData.ownInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY))

        // Disable autoCommit on connection - we want to add whole XES log structure
        connection.autoCommit = false
    }

    /**
     * Write XES Element into the database
     */
    override fun write(component: XESComponent) {
        when (component) {
            is Event -> {
                // We expect that the corresponding Trace object is already stored in the database.
                // If only Log is stored then we encountered an event stream.
                if (traceId === null) {
                    val eventStreamTraceElement = Trace()
                    // Set trace as event stream - special boolean flag
                    eventStreamTraceElement.isEventStream = true
                    write(eventStreamTraceElement)
                }

                eventQueue.add(component)
                if (eventQueue.size >= batchSize)
                    flushEvents()
            }
            is Trace -> {
                // We expect to already store Log object in the database
                check(logId !== null) { "Log ID not set. Can not add trace to the database" }

                flushEvents() // flush events from the previous trace

                val sql = SQL()
                writeTrace(component, sql)
                writeAttributes("TRACES_ATTRIBUTES", "trace", 0, component.attributes.values, sql)
                traceId = sql.executeQuery("trace")
            }
            is Log -> {
                flushEvents() // flush events from the previous log

                val sql = SQL()
                writeLog(component, sql)
                writeExtensions(component.extensions.values, sql)
                writeClassifiers("event", component.eventClassifiers.values, sql)
                writeClassifiers("trace", component.traceClassifiers.values, sql)
                writeGlobals("event", component.eventGlobals.values, sql)
                writeGlobals("trace", component.traceGlobals.values, sql)
                writeAttributes("LOGS_ATTRIBUTES", "log", 0, component.attributes.values, sql)
                logId = sql.executeQuery("log").toInt()
            }
            else ->
                throw IllegalArgumentException("Unsupported XESComponent found. Expected 'Log', 'Trace' or 'Event' but received ${component.javaClass}")
        }
    }

    /**
     * Commit and close connection with the database
     */
    override fun close() {
        flushEvents()
        connection.commit()
        connection.close()
    }

    /**
     * Rollback transaction and close connection.
     *
     * Should be used when receive Exception from `write` function.
     */
    override fun abort() {
        connection.rollback()
        connection.close()
    }

    private fun flushEvents() {
        if (eventQueue.isEmpty())
            return

        val sql = SQL()
        writeEvents(eventQueue, sql)
        for ((index, event) in eventQueue.withIndex()) {
            writeAttributes("EVENTS_ATTRIBUTES", "event", index, event.attributes.values, sql)
        }
        eventQueue.clear()
        sql.execute()

        assert(eventQueue.isEmpty())
    }

    private fun writeEvents(events: Iterable<Event>, to: SQL) {
        with(to.sql) {
            append("WITH event AS (")
            append("""INSERT INTO EVENTS(trace_id, "concept:name", "concept:instance", "cost:total", "cost:currency", "identity:id", "lifecycle:transition", "lifecycle:state", "org:resource", "org:role", "org:group", "time:timestamp") VALUES """)
        }

        for (event in events) {
            to.sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), ")

            with(to.params) {
                addLast(traceId)
                addLast(event.conceptName)
                addLast(event.conceptInstance)
                addLast(event.costTotal)
                addLast(event.costCurrency)
                addLast(event.identityId)
                addLast(event.lifecycleTransition)
                addLast(event.lifecycleState)
                addLast(event.orgResource)
                addLast(event.orgRole)
                addLast(event.orgGroup)
                addLast(event.timeTimestamp?.let { Timestamp.from(it) })
            }
        }

        with(to.sql) {
            delete(length - 2, length)
            append(" RETURNING ID)")
        }
    }

    private fun writeTrace(element: Trace, to: SQL) {
        with(to.sql) {
            append("WITH trace AS (")
            append("""INSERT INTO TRACES(log_id, "concept:name", "cost:total", "cost:currency", "identity:id", event_stream) VALUES (?, ?, ?, ?, ?, ?) RETURNING ID""")
            append(')')
        }

        with(to.params) {
            addLast(logId)
            addLast(element.conceptName)
            addLast(element.costTotal)
            addLast(element.costCurrency)
            addLast(element.identityId)
            addLast(element.isEventStream)
        }
    }

    private fun writeLog(element: Log, to: SQL) {
        with(to.sql) {
            append("WITH log AS (")
            append("""INSERT INTO LOGS("xes:version", "xes:features", "concept:name", "identity:id", "lifecycle:model") VALUES (?, ?, ?, ?, ?) RETURNING ID""")
            append(')')
        }

        with(to.params) {
            addLast(element.xesVersion)
            addLast(element.xesFeatures)
            addLast(element.conceptName)
            addLast(element.identityId)
            addLast(element.lifecycleModel)
        }
    }

    private fun writeAttributes(
        destinationTable: String,
        rootTempTable: String,
        rootIndex: Int,
        attributes: Collection<Attribute<*>>,
        to: SQL,
        extraColumns: Map<String, String> = emptyMap()
    ) {
        if (attributes.isEmpty())
            return

        fun addAttributes(
            attributes: Iterable<Attribute<*>>,
            parentTableNumber: Int = 0,
            parentRowIndex: Int = 0,
            topMost: Boolean = true,
            inList: Boolean? = null
        ) {
            // This function preseves the order of attributes on each level of the tree but does not preserve the order
            // between levels. This is enough to preserve the order of list attribute. It cannot use the (straightforward)
            // depth-first-search algorithm, as writable common table extensions in PostgreSQL are evaluated concurrently.
            // From https://www.postgresql.org/docs/current/queries-with.html:
            // The sub-statements in WITH are executed concurrently with each other and with the main query. Therefore,
            // when using data-modifying statements in WITH, the order in which the specified updates actually happen is
            // unpredictable. All the statements are executed with the same snapshot (see Chapter 13), so they cannot
            // “see” one another's effects on the target tables. This alleviates the effects of the unpredictability of
            // the actual order of row updates, and means that RETURNING data is the only way to communicate changes
            // between different WITH sub-statements and the main query.
            val myTableNumber = ++to.attrSeq
            with(to.sql) {
                append(", attributes$myTableNumber AS (")
                append(
                    "INSERT INTO $destinationTable(${rootTempTable}_id, key, type, " +
                            "string_value, uuid_value, date_value, int_value, bool_value, real_value, " +
                            "parent_id, in_list_attr ${extraColumns.keys.join()}) "
                )
                append(
                    "SELECT (SELECT id FROM $rootTempTable ORDER BY id LIMIT 1 OFFSET $rootIndex), a.key, a.type, " +
                            "a.string_value, a.uuid_value, a.date_value, a.int_value, a.bool_value, a.real_value, " +
                            "${if (topMost) "NULL" else "(SELECT id FROM attributes$parentTableNumber ORDER BY id LIMIT 1 OFFSET $parentRowIndex)"}, " +
                            "a.in_list_attr ${extraColumns.values.join { "'$it'" }} FROM (VALUES "
                )
            }
            for (attribute in attributes) {
                to.sql.append("(?, ?::attribute_type, ?, ?::uuid, ?::timestamptz, ?::integer, ?::boolean, ?::double precision, ?::boolean), ")
                with(to.params) {
                    addLast(attribute.key)
                    addLast(attribute.xesTag)
                    addLast((attribute as? StringAttr)?.value)
                    addLast((attribute as? IDAttr)?.value)
                    addLast((attribute as? DateTimeAttr)?.value?.let { Timestamp.from(it) })
                    addLast((attribute as? IntAttr)?.value)
                    addLast((attribute as? BoolAttr)?.value)
                    addLast((attribute as? RealAttr)?.value)
                    addLast(inList)
                }
            }

            with(to.sql) {
                delete(length - 2, length)
                append(") a(key, type, string_value, uuid_value, date_value, int_value, bool_value, real_value, in_list_attr) ")
                append("RETURNING id)")
            }

            // Handle children and lists
            for ((index, attribute) in attributes.withIndex()) {
                // Advance to children
                if (attribute.children.isNotEmpty())
                    addAttributes(attribute.children.values, myTableNumber, index, false)

                // Handle list
                if (attribute is ListAttr && attribute.value.isNotEmpty()) {
                    addAttributes(attribute.value, myTableNumber, index, false, true)
                }
            }
        }

        addAttributes(attributes)
    }

    private fun writeExtensions(extensions: Collection<Extension>, to: SQL) {
        if (extensions.isEmpty())
            return

        with(to.sql) {
            append(", extensions AS (INSERT INTO EXTENSIONS (log_id, name, prefix, uri) ")
            append("SELECT log.id, e.name, e.prefix, e.uri FROM log, (VALUES ")
        }
        for (extension in extensions) {
            to.sql.append("(?, ?, ?), ")
            with(to.params) {
                addLast(extension.name)
                addLast(extension.prefix)
                addLast(extension.uri)
            }
        }
        to.sql.delete(to.sql.length - 2, to.sql.length)
        to.sql.append(") e(name, prefix, uri))")
    }

    private fun writeClassifiers(scope: String, classifiers: Collection<Classifier>, to: SQL) {
        if (classifiers.isEmpty())
            return
        with(to.sql) {
            append(", classifiers$scope AS (")
            append("INSERT INTO CLASSIFIERS(log_id, scope, name, keys) ")
            append("SELECT log.id, c.scope, c.name, c.keys FROM log, (VALUES ")
        }

        for (classifier in classifiers) {
            to.sql.append("(?::scope_type, ?, ?), ")
            with(to.params) {
                addLast(scope)
                addLast(classifier.name)
                addLast(classifier.keys)
            }
        }
        to.sql.delete(to.sql.length - 2, to.sql.length)
        to.sql.append(") c(scope, name, keys))")
    }

    private fun writeGlobals(scope: String, globals: Collection<Attribute<*>>, to: SQL) =
        writeAttributes("GLOBALS", "log", 0, globals, to, mapOf("scope" to scope))

    private fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
        for (item in this@join) {
            append(", ")
            append(transform(item))
        }
    }

    private inner class SQL {
        var attrSeq: Int = 0
        val sql: StringBuilder = StringBuilder()
        val params: LinkedList<Any> = LinkedList()

        @Suppress("SqlResolve")
        fun executeQuery(table: String): Long {
            connection.prepareStatement("$sql SELECT id FROM $table").use {
                for ((i, obj) in params.withIndex()) {
                    it.setObject(i + 1, obj)
                }
                it.executeQuery().use { r ->
                    check(r.next()) { "Write unsuccessful." }
                    return@executeQuery r.getLong(1)
                }
            }
        }

        fun execute() {
            connection.prepareStatement("$sql SELECT 1 LIMIT 0").use {
                for ((i, obj) in params.withIndex()) {
                    it.setObject(i + 1, obj)
                }
                check(it.execute()) { "Write unsuccessful." }
            }
        }
    }
}

@Deprecated("Class was renamed. Type alias is provided for backward-compatibility.")
typealias DatabaseXESOutputStream = DBXESOutputStream
