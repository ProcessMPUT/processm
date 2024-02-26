package processm.core.log

import processm.core.log.attribute.AttributeMap
import processm.core.log.attribute.xesTag
import processm.core.persistence.copy.Copy
import processm.core.persistence.copy.EagerCopy
import processm.core.persistence.copy.LazyCopy
import processm.core.querylanguage.Scope
import processm.helpers.toUUID
import processm.logging.loggedScope
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

open class DBXESOutputStream protected constructor(
    protected val connection: Connection,
    val isAppending: Boolean,
    val batchSize: Int = DBXESOutputStream.batchSize
) :
    XESOutputStream {
    companion object {
        internal const val batchSize = 1024 * 1024    //An arbitrarily chosen value

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

    constructor(connection: Connection) : this(connection, false) {}

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

    private val tempTracesTable = """"${UUID.randomUUID()}""""
    private val tempEventsTable = """"${UUID.randomUUID()}""""

    init {
        assert(connection.metaData.supportsGetGeneratedKeys())
        assert(connection.metaData.supportsTransactions())
        assert(connection.metaData.ownInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY))

        // Disable autoCommit on connection - we want to add whole XES log structure
        connection.autoCommit = false

        connection.createStatement().use { stmt ->
            stmt.execute(
                """CREATE TEMPORARY TABLE $tempTracesTable (LIKE TRACES INCLUDING DEFAULTS INCLUDING GENERATED) ON COMMIT DROP; 
            CREATE TEMPORARY TABLE $tempEventsTable (LIKE EVENTS INCLUDING DEFAULTS INCLUDING GENERATED) ON COMMIT DROP"""
            )
        }
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
                copyForAttributes("GLOBALS", "log", mapOf("scope" to "event")) { a, b ->
                    EagerCopy(connection, a, listOf(logId!!.toString()), b)
                }.use {
                    writeAttributes(component.eventGlobals, it)
                    it.setExtraColumnValues(listOf("trace"))
                    writeAttributes(component.traceGlobals, it)
                }
                if (component.attributes.isNotEmpty()) {
                    copyForAttributes("LOGS_ATTRIBUTES", "log") { a, b ->
                        EagerCopy(connection, a, prefix = listOf(logId!!.toString()), b)
                    }.use {
                        writeAttributes(component.attributes, it)
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

        val known = HashMap<UUID, Long>()
        if (isAppending) {
            with(SQL()) {
                sql.append("""SELECT TRACES."identity:id", TRACES.id FROM TRACES JOIN UNNEST(?) tmp("identity:id") ON TRACES."identity:id" = tmp."identity:id"::uuid AND TRACES.log_id = ?""")
                val traceIdentities = queue.filterIsInstance<Trace>().mapNotNull { it.identityId }
                params.addLast(traceIdentities.toTypedArray())
                params.addLast(logId)
                executeQuery { rs ->
                    while (rs.next()) {
                        known[rs.getString(1).toUUID()!!] = rs.getLong(2)
                    }
                }
            }
        }

        val eventCopy =
            LazyCopy("""$tempEventsTable(trace_id,"concept:name","concept:instance","cost:total","cost:currency","identity:id","lifecycle:transition","lifecycle:state","org:resource","org:role","org:group","time:timestamp")""")
        val traceAttrCopy = copyForAttributes("TRACES_ATTRIBUTES", "trace")
        val eventAttrCopy = copyForAttributes("EVENTS_ATTRIBUTES", "event")

        val intermediateTraceIds = ArrayList<Long?>()

        var lastEventIndex = -1
        var lastTraceIndex = -1
        EagerCopy(
            connection,
            """$tempTracesTable(log_id,"concept:name","cost:total","cost:currency","identity:id",event_stream)"""
        ).use { traceCopy ->
            for (component in queue) {
                when (component) {
                    is Event -> {
                        check(lastTraceIndex >= 0) { "Trace must precede event in the queue." }
                        ++lastEventIndex
                        writeEventData(component, eventCopy, lastTraceIndex)
                        writeAttributes(lastEventIndex, component.attributes, eventAttrCopy)
                    }

                    is Trace -> {
                        ++lastTraceIndex
                        if (isAppending) {
                            val existingTraceId = component.identityId?.let { known[it] }
                            if (existingTraceId === null)
                                writeTraceData(component, traceCopy)
                            intermediateTraceIds.add(existingTraceId)
                        } else
                            writeTraceData(component, traceCopy)
                        writeAttributes(lastTraceIndex, component.attributes, traceAttrCopy)
                    }

                    else -> throw UnsupportedOperationException("Unexpected $component.")
                }
            }
        }

        assert(lastTraceIndex >= 0)

        val traceIds = ArrayList<Long>()
        connection.prepareStatement("SELECT id FROM $tempTracesTable").executeQuery().use { rs ->
            if (isAppending) {
                for (i in 0..lastTraceIndex) {
                    traceIds.add(intermediateTraceIds[i] ?: run {
                        check(rs.next())
                        rs.getLong(1)
                    })
                }
            } else
                while (rs.next())
                    traceIds.add(rs.getLong(1))
        }
        check(traceIds.size == lastTraceIndex + 1) { "Write unsuccessful." }

        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO TRACES SELECT * FROM $tempTracesTable; TRUNCATE $tempTracesTable")
        }

        eventCopy.execute(connection, traceIds)

        val eventIds = ArrayList<Long>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id FROM $tempEventsTable").use { rs ->
                while (rs.next()) {
                    eventIds.add(rs.getLong(1))
                }
            }
        }
        check(eventIds.size == lastEventIndex + 1) { "Write unsuccessful." }

        connection.createStatement().use { stmt ->
            stmt.execute("INSERT INTO EVENTS SELECT * FROM $tempEventsTable; TRUNCATE $tempEventsTable")
        }

        eventAttrCopy.execute(connection, eventIds)
        traceAttrCopy.execute(connection, traceIds)


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

    private fun writeEventData(event: Event, to: LazyCopy, traceIndex: Int) {
        with(to) {
            add(event.conceptName)
            add(event.conceptInstance)
            add(event.costTotal)
            add(event.costCurrency)
            add(event.identityId)
            add(event.lifecycleTransition)
            add(event.lifecycleState)
            add(event.orgResource)
            add(event.orgRole)
            add(event.orgGroup)
            add(event.timeTimestamp)

            flushRow(traceIndex)
        }
    }

    private fun writeTraceData(trace: Trace, to: EagerCopy) {
        with(to) {
            add(logId?.toLong())
            add(trace.conceptName)
            add(trace.costTotal)
            add(trace.costCurrency)
            add(trace.identityId)
            add(trace.isEventStream)
            flushRow()
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


    private fun <T> copyForAttributes(
        destinationTable: String,
        rootTempTable: String,
        extraColumns: Map<String, String> = emptyMap(), ctor: (String, Collection<String>) -> T
    ): T = ctor(
        "$destinationTable (${rootTempTable}_id, type, key, string_value, uuid_value, date_value, int_value, bool_value, real_value ${extraColumns.keys.join()})",
        extraColumns.values
    )

    private fun copyForAttributes(
        destinationTable: String,
        rootTempTable: String,
        extraColumns: Map<String, String> = emptyMap()
    ): LazyCopy = copyForAttributes(destinationTable, rootTempTable, extraColumns, ::LazyCopy)

    private fun Copy.addAttribute(attribute: Map.Entry<CharSequence, Any?>) {
        add(attribute.value.xesTag)
        add(attribute.key.toString())
        add(attribute.value as? String)
        add(attribute.value as? UUID)
        add(attribute.value as? Instant)
        add(attribute.value as? Long)
        add(attribute.value as? Boolean)
        add(attribute.value as? Double)
    }

    private fun writeAttributes(
        rootIndex: Int,
        attributes: AttributeMap,
        to: LazyCopy,
    ) {
        if (attributes.isEmpty())
            return
        with(to) {
            for (attribute in attributes.flatView) {
                addAttribute(attribute)
                flushRow(rootIndex)
            }
        }
    }

    private fun writeAttributes(attributes: AttributeMap, to: EagerCopy) {
        if (attributes.isEmpty())
            return
        with(to) {
            for (attribute in attributes.flatView) {
                addAttribute(attribute)
                flushRow()
            }
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
