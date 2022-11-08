package processm.core.log

import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import processm.core.log.attribute.AttributeMap
import processm.core.logging.loggedScope
import processm.core.querylanguage.Scope
import java.io.PrintStream
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.reflect.KClass

open class DBXESOutputStream(protected val connection: Connection) : XESOutputStream {
    companion object {
        internal const val batchSize = 384

        /**
         * The limit of the number of parameters in an SQL query. When exceeded, no new trace will be inserted in the
         * current batch. The current trace will be still completed.
         */
        internal const val paramSoftLimit = Short.MAX_VALUE - 8192

        private const val analyzeThreshold = Short.MAX_VALUE

        private val ISO8601 = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

        private val tagStartChars = ('a'..'z') + ('A'..'Z') + ('_')
        private val tagChars = ('0'..'9') + tagStartChars

        @JvmStatic
        protected fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
            for (item in this@join) {
                append(", ")
                append(transform(item))
            }
        }
    }

    /**
     * Log ID of inserted Log record
     */
    protected var logId: Int? = null

    /**
     * Did we see trace component in the current log?
     */
    protected var sawTrace: Boolean = false

    /**
     * A buffer of traces and events to write to the database together. Must contain complete traces.
     */
    protected var queue = ArrayList<XESComponent>(batchSize)

    /**
     * The counter of the total number of XES components inserted into the database. When exceeds [analyzeThreshold],
     * the [close] method of this stream calls ANALYZE in the database to recalculate statistics.
     */
    private var totalInserts: Long = 0L

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
                // We expect that the corresponding Trace object is already stored in the queue.
                // Otherwise we encountered an event stream.
                if (!sawTrace) {
                    val eventStreamTraceElement = Trace()
                    // Set trace as event stream - special boolean flag
                    eventStreamTraceElement.isEventStream = true
                    write(eventStreamTraceElement)
                }

                queue.add(component)
            }

            is Trace -> {
                if (queue.size >= batchSize)
                    flushQueue(false)

                // We expect to already store Log object in the database
                check(logId !== null) { "Log ID not set. Can not add trace to the database" }

                queue.add(component)
                sawTrace = true
            }

            is Log -> {
                flushQueue(true) // flush events and traces from the previous log
                sawTrace = false // we must not refer to a trace from the previous log

                val sql = SQL()
                writeLog(component, sql)
                writeExtensions(component.extensions.values, sql)
                writeClassifiers(Scope.Event, component.eventClassifiers.values, sql)
                writeClassifiers(Scope.Trace, component.traceClassifiers.values, sql)
                logId = sql.executeQuery("log").toInt()
                writeGlobals("event", component.eventGlobals)?.execute(connection, listOf(logId!!.toLong()))
                writeGlobals("trace", component.traceGlobals)?.execute(connection, listOf(logId!!.toLong()))
                if (component.attributes.isNotEmpty()) {
                    with(Copy("LOGS_ATTRIBUTES", "log")) {
                        writeAttributes(0, component.attributes, this)
                        execute(connection, listOf(logId!!.toLong()))
                    }
                }
            }

            else ->
                throw IllegalArgumentException("Unsupported XESComponent found. Expected 'Log', 'Trace' or 'Event' but received ${component.javaClass}")
        }
    }

    /**
     * Flushes the internal buffers to the underlying database.
     */
    fun flush() {
        flushQueue(true)
    }

    /**
     * Commit and close connection with the database
     */
    override fun close() = loggedScope { logger ->
        flush()
        with(connection) {
            if (totalInserts >= analyzeThreshold) {
                createStatement().use { stmt ->
                    logger.trace("Running ANALYZE after inserting $totalInserts XES components into database.")
                    // ANALYZE sees the uncommitted rows inserted in this transaction
                    // https://stackoverflow.com/a/71653180
                    stmt.execute("ANALYZE (SKIP_LOCKED) logs, logs_attributes, globals, extensions, classifiers, traces, traces_attributes, events, events_attributes")
                    logger.trace("ANALYZE done.")
                }
            }
            commit()
            close()
        }
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

    protected open fun flushQueue(force: Boolean) {
        if (queue.isEmpty())
            return

        val traceSql = SQL()
        val eventSql = SQL()
        val traceAttrCopy = Copy("TRACES_ATTRIBUTES", "trace")
        val eventAttrCopy = Copy("EVENTS_ATTRIBUTES", "event")

        with(traceSql.sql) {
            append("WITH trace AS (")
            append("""INSERT INTO TRACES(log_id,"concept:name","cost:total","cost:currency","identity:id",event_stream) VALUES""")
        }

        with(eventSql.sql) {
            append(", event AS (")
            append("""INSERT INTO EVENTS(trace_id,"concept:name","concept:instance","cost:total","cost:currency","identity:id","lifecycle:transition","lifecycle:state","org:resource","org:role","org:group","time:timestamp") VALUES""")
        }

        var lastEventIndex = -1
        var lastTraceIndex = -1
        for (component in queue) {
            when (component) {
                is Event -> {
                    check(lastTraceIndex >= 0) { "Trace must precede event in the queue." }
                    ++lastEventIndex
                    writeEventData(component, eventSql, lastTraceIndex)
                    writeAttributes(lastEventIndex, component.attributes, eventAttrCopy)
                }

                is Trace -> {
                    if (traceSql.params.size + eventSql.params.size >= paramSoftLimit) {
                        // #102: if the total number of parameters in an SQL query is too large, then DO NOT start new trace
                        break
                    }
                    ++lastTraceIndex
                    writeTraceData(component, traceSql)
                    writeAttributes(lastTraceIndex, component.attributes, traceAttrCopy)
                }

                else -> throw UnsupportedOperationException("Unexpected $component.")
            }
        }

        assert(lastTraceIndex >= 0)

        with(traceSql.sql) {
            delete(length - 2, length)
            append(" RETURNING id)")
        }

        with(eventSql.sql) {
            delete(length - 2, length)
            append(" RETURNING id)")
        }

        with(traceSql) {
            if (lastEventIndex >= 0) {
                sql.append(eventSql.sql)
                params.addAll(eventSql.params)
            }

            sql.append(" SELECT id, 0 as type FROM event UNION ALL SELECT id, 1 as type FROM trace")
            val eventIds = ArrayList<Long>()
            val traceIds = ArrayList<Long>()
            executeQuery { rs ->
                while (rs.next()) {
                    val type = rs.getLong(2)
                    val id = rs.getLong(1)
                    when (type) {
                        0L -> eventIds.add(id)
                        1L -> traceIds.add(id)
                    }
                }
            }
            check(eventIds.size == lastEventIndex + 1) { "Write unsuccessful." }
            check(traceIds.size == lastTraceIndex + 1) { "Write unsuccessful." }
            eventAttrCopy.execute(connection, eventIds)
            traceAttrCopy.execute(connection, traceIds)
        }

        clearQueue(lastEventIndex, lastTraceIndex, force)
    }

    protected fun clearQueue(lastEventIndex: Int, lastTraceIndex: Int, force: Boolean) {
        val countItemsToInsert = lastEventIndex + lastTraceIndex + 2
        assert(countItemsToInsert in 1..queue.size)
        if (countItemsToInsert == queue.size) {
            queue.clear()
            assert(queue.isEmpty())
        } else {
            // #102: if the total number of parameters in an SQL query is too large, keep the remaining traces and events in the queue
            queue = ArrayList(queue.subList(countItemsToInsert, queue.size))
            // #102: when ending the log, we must flush the queue
            if (force) {
                flushQueue(force)
                assert(queue.isEmpty())
            }
        }
        totalInserts += countItemsToInsert
    }

    protected fun writeEventData(event: Event, to: SQL, traceIndex: Int?) {
        with(to) {
            sql.append("((SELECT id FROM trace LIMIT 1 OFFSET $traceIndex),")

            addAsParamOrInline(event.conceptName)
            addAsParamOrInline(event.conceptInstance)
            addAsParamOrInline(event.costTotal)
            addAsParamOrInline(event.costCurrency)
            addAsParamOrInline(event.identityId)
            addAsParamOrInline(event.lifecycleTransition)
            addAsParamOrInline(event.lifecycleState)
            addAsParamOrInline(event.orgResource)
            addAsParamOrInline(event.orgRole)
            addAsParamOrInline(event.orgGroup)
            addAsParamOrInline(event.timeTimestamp, "")

            sql.append("), ")
        }
    }

    protected fun writeTraceData(trace: Trace, to: SQL) {
        with(to) {
            sql.append('(')

            addAsParamOrInline(logId)
            addAsParamOrInline(trace.conceptName)
            addAsParamOrInline(trace.costTotal)
            addAsParamOrInline(trace.costCurrency)
            addAsParamOrInline(trace.identityId)
            addAsParamOrInline(trace.isEventStream, "")

            sql.append("), ")
        }
    }

    private fun writeLog(element: Log, to: SQL) {
        with(to) {
            sql.append("WITH log AS (")
            sql.append("""INSERT INTO LOGS("xes:version","xes:features","concept:name","identity:id","lifecycle:model") VALUES (""")

            addAsParamOrInline(element.xesVersion)
            addAsParamOrInline(element.xesFeatures)
            addAsParamOrInline(element.conceptName)
            addAsParamOrInline(element.identityId)
            addAsParamOrInline(element.lifecycleModel, "")

            sql.append(") RETURNING id)")
        }
    }

    /**
     * A wrapper around Postgres's COPY FROM STDIN handling escaping and mapping indexes to real IDs
     */
    private class Copy(val destination: String, extraColumnValues: Collection<String>) {


        constructor(
            destinationTable: String,
            rootTempTable: String,
            extraColumns: Map<String, String> = emptyMap()
        ) : this(
            "$destinationTable (${rootTempTable}_id, type, key, string_value, uuid_value, date_value, int_value, bool_value, real_value ${extraColumns.keys.join()})",
            extraColumns.values
        )

        companion object {
            //TODO these two are current defaults, but maybe they should be passed in the query in order to make the query more robust?
            const val NULL = "\\N"
            const val DELIMITER = '\t'
        }

        val data = ArrayList<Pair<Int, String>>()

        private var current = StringBuilder()
        private val suffix: String

        init {
            if (extraColumnValues.isNotEmpty()) {
                extraColumnValues.forEach(::addInternal)
                suffix = current.toString()
                current.clear()
            } else
                suffix = ""
        }

        private fun addInternal(text: String?) = with(current) {
            append(DELIMITER)
            if (text !== null)
                append(text)
            else
                append(NULL)
        }

        private fun addInternal(text: StringBuilder?) = with(current) {
            append(DELIMITER)
            if (text !== null)
                append(text)
            else
                append(NULL)
        }

        fun add(value: String?) {
            //While this seems expensive, all my tries on making it more efficient by considering multiple characters at once and using StringBuilder failed
            //I hypothesise that most of these characters don't occur in most of the strings, so replace can short-circuit and return the same string
            addInternal(
                value
                    ?.replace("\\", "\\\\")
                    ?.replace("\b", "\\b")
                    ?.replace("\u000c", "\\f")
                    ?.replace("\n", "\\n")
                    ?.replace("\r", "\\r")
                    ?.replace("\t", "\\t")
                    ?.replace("\u000b", "\\v")
            )
        }

        //TODO can any other datatype yield one of the special characters?

        fun add(value: UUID?) {
            addInternal(value?.toString())
        }

        fun add(value: Instant?) {
            if (value !== null)
                addInternal(ISO8601.format(value))
            else
                addInternal(null as String?)
        }

        fun add(value: Long?) {
            addInternal(value?.toString())
        }

        fun add(value: Boolean?) {
            addInternal(value?.toString())   //TODO verify
        }

        fun add(value: Double?) {
            addInternal(value?.toString())   //TODO verify
        }

        fun flushRow(rootIndex: Int) {
            data.add(rootIndex to current.toString())
            current.clear()
        }

        fun execute(connection: Connection, ids: List<Long>) {
            if (data.isEmpty())
                return
            val sql =
                "COPY $destination FROM STDIN"
            val pgConnection = connection.unwrap(PGConnection::class.java)
            PGCopyOutputStream(pgConnection, sql).use {
                PrintStream(it).use { out ->
                    for ((idx, row) in data) {
                        out.print(ids[idx])
                        out.print(row)
                        out.println(suffix)
                    }
                }
            }
        }

    }


    private fun writeAttributes(
        rootIndex: Int,
        attributes: AttributeMap,
        to: Copy,
    ) {
        if (attributes.isEmpty())
            return
        with(to) {
            for (attribute in attributes.flat) {
                add(attribute.value.xesTag)
                add(attribute.key)
                add(attribute.value as? String)
                add(attribute.value as? UUID)
                add(attribute.value as? Instant)
                add(attribute.value as? Long)
                add(attribute.value as? Boolean)
                add(attribute.value as? Double)
                flushRow(rootIndex)
            }
        }
    }

    protected fun writeTypedAttribute(attribute: Any?, type: KClass<*>, to: SQL, writeCast: Boolean) {
        val cast = if (writeCast) {
            when (type) {
                String::class -> ""
                UUID::class -> "::uuid"
                Instant::class -> "::timestamptz"
                Long::class -> "::bigint"
                Boolean::class -> "::boolean"
                Double::class -> "::double precision"
                else -> throw UnsupportedOperationException("Unknown attribute type $type.")
            }
        } else ""
        if (type.isInstance(attribute)) {
            when (type) {
                Long::class, Boolean::class -> to.sql.append("${attribute}$cast,")
                UUID::class -> to.sql.append("'${attribute}'$cast,")
                Instant::class -> to.sql.append("'${ISO8601.format(attribute as Instant)}'$cast,")
                else -> {
                    to.sql.append("?$cast,")
                    to.params.addLast(attribute)
                }
            }
        } else {
            to.sql.append("NULL$cast,")
        }
    }

    private fun writeExtensions(extensions: Collection<Extension>, to: SQL) {
        if (extensions.isEmpty())
            return

        with(to.sql) {
            append(", extensions AS (INSERT INTO EXTENSIONS (log_id,name,prefix,uri) ")
            append("SELECT log.id,e.name,e.prefix,e.uri FROM log, (VALUES ")
        }
        for (extension in extensions) {
            to.sql.append("(?,?,?),")
            with(to.params) {
                addLast(extension.name)
                addLast(extension.prefix)
                addLast(extension.uri)
            }
        }
        to.sql.deleteCharAt(to.sql.length - 1)
        to.sql.append(") e(name,prefix,uri))")
    }

    private fun writeClassifiers(scope: Scope, classifiers: Collection<Classifier>, to: SQL) {
        if (classifiers.isEmpty())
            return
        with(to.sql) {
            append(", classifiers$scope AS (")
            append("INSERT INTO CLASSIFIERS(log_id,scope,name,keys) ")
            append("SELECT log.id,c.scope,c.name,c.keys FROM log, (VALUES ")
        }

        var first = true
        for (classifier in classifiers) {
            to.sql.append("('$scope'")
            if (first) {
                to.sql.append("::scope_type")
                first = false
            }
            to.sql.append(",?,?),")
            with(to.params) {
                addLast(classifier.name)
                addLast(classifier.keys)
            }
        }
        assert(!first)
        to.sql.deleteCharAt(to.sql.length - 1)
        to.sql.append(") c(scope,name,keys))")
    }

    private fun writeGlobals(scope: String, globals: AttributeMap): Copy? =
        if (globals.isNotEmpty())
            Copy("GLOBALS", "log", mapOf("scope" to scope)).apply {
                writeAttributes(0, globals, this)
            }
        else null

    protected inner class SQL {
        var attrSeq: Int = 0
        val sql: StringBuilder = StringBuilder()
        val params: LinkedList<Any> = LinkedList()

        @Suppress("SqlResolve")
        fun executeQuery(table: String): Long {
            inlineParamsOverLimit()
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

        /**
         * [use] is responsible for verifying whether there is a correct number of rows and throwing an exception otherwise
         */
        fun <T> executeQuery(use: (ResultSet) -> T): T {
            inlineParamsOverLimit()
            connection.prepareStatement("$sql").use {
                for ((i, obj) in params.withIndex()) {
                    if (obj is Array<*>)
                        it.setArray(i + 1, connection.createArrayOf("text", obj))
                    else
                        it.setObject(i + 1, obj)
                }
                return it.executeQuery().use(use)
            }
        }

        fun execute() {
            inlineParamsOverLimit()
            connection.prepareStatement("$sql SELECT 1 LIMIT 0").use {
                for ((i, obj) in params.withIndex()) {
                    if (obj is Array<*>)
                        it.setArray(i + 1, connection.createArrayOf("text", obj))
                    else
                        it.setObject(i + 1, obj)
                }
                check(it.execute()) { "Write unsuccessful." }
            }
        }

        fun <T> addAsParamOrInline(v: T?, suffix: String = ",") {
            when (v) {
                null -> sql.append("NULL")
                is Instant -> sql.append("'${ISO8601.format(v)}'")
                else -> {
                    sql.append('?')
                    params.addLast(v)
                }
            }
            sql.append(suffix)
        }

        /**
         * Inlines the parameters of the query being having indices greater than [Short.MAX_VALUE].
         * See #102
         */
        private fun inlineParamsOverLimit() {
            var lastIndex = sql.length
            while (params.size > Short.MAX_VALUE) {
                val index = sql.lastIndexOf("?", lastIndex)
                if (index == -1)
                    return
                val replacement = when (val param = params.removeLast()) {
                    is Double, is Long, is Boolean -> param.toString()
                    is Instant -> "'${ISO8601.format(param)}'"
                    else -> {
                        val paramAsString = param.toString()
                        val tag = getEscapeTag(paramAsString)
                        "$$tag$$paramAsString$$tag$"
                    }
                }
                sql.replace(index, index + 1, replacement)
                lastIndex = index - 1
            }
        }

        /**
         * See https://stackoverflow.com/a/9742217
         */
        private fun getEscapeTag(text: String): String {
            val tag = StringBuilder(tagStartChars.random().toString())
            while (text.contains(tag)) {
                tag.append(tagChars.random())
            }
            return tag.toString()
        }
    }
}

@Deprecated("Class was renamed. Type alias is provided for backward-compatibility.")
typealias DatabaseXESOutputStream = DBXESOutputStream
