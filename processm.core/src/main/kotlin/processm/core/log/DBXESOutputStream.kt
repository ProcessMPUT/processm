package processm.core.log

import processm.core.log.attribute.*
import processm.core.querylanguage.Scope
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

        private val ISO8601 = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

        private val tagStartChars = ('a'..'z') + ('A'..'Z') + ('_')
        private val tagChars = ('0'..'9') + tagStartChars
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
        flushQueue(true)
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

    protected open fun flushQueue(force: Boolean) {
        if (queue.isEmpty())
            return

        val traceSql = SQL()
        val eventSql = SQL()
        val attrSql = SQL()

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
                    writeAttributes("EVENTS_ATTRIBUTES", "event", lastEventIndex, component.attributes.values, attrSql)
                }
                is Trace -> {
                    if (traceSql.params.size + eventSql.params.size + attrSql.params.size >= paramSoftLimit) {
                        // #102: if the total number of parameters in an SQL query is too large, then DO NOT start new trace
                        break
                    }
                    ++lastTraceIndex
                    writeTraceData(component, traceSql)
                    writeAttributes("TRACES_ATTRIBUTES", "trace", lastTraceIndex, component.attributes.values, attrSql)
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

            sql.append(attrSql.sql)
            params.addAll(attrSql.params)
            execute()
        }

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
            // This function preserves the order of attributes on each level of the tree but does not preserve the order
            // between the levels. This is enough to preserve the order of list attribute. It cannot use the (straightforward)
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
                            "parent_id, in_list_attr${extraColumns.keys.join()}) "
                )
                append(
                    "SELECT (SELECT id FROM $rootTempTable ORDER BY id LIMIT 1 OFFSET $rootIndex), a.key, a.type, " +
                            "a.string_value, a.uuid_value, a.date_value, a.int_value, a.bool_value, a.real_value, " +
                            "${if (topMost) "NULL" else "(SELECT id FROM attributes$parentTableNumber ORDER BY id LIMIT 1 OFFSET $parentRowIndex)"}, " +
                            "a.in_list_attr${extraColumns.values.join { "'$it'" }} FROM (VALUES "
                )
            }
            with(to) {
                var first = true
                for (attribute in attributes) {
                    sql.append("(?,'${attribute.xesTag}'")
                    if (first)
                        sql.append("::attribute_type")
                    sql.append(',')
                    params.addLast(attribute.key)
                    writeTypedAttribute(attribute, StringAttr::class, to, first)
                    writeTypedAttribute(attribute, IDAttr::class, to, first)
                    writeTypedAttribute(attribute, DateTimeAttr::class, to, first)
                    writeTypedAttribute(attribute, IntAttr::class, to, first)
                    writeTypedAttribute(attribute, BoolAttr::class, to, first)
                    writeTypedAttribute(attribute, RealAttr::class, to, first)
                    sql.append(inList)
                    if (first) {
                        sql.append("::boolean")
                        first = false
                    }
                    sql.append("),")
                }
                assert(!first)
            }

            with(to.sql) {
                deleteCharAt(length - 1)
                append(") a(key,type,string_value,uuid_value,date_value,int_value,bool_value,real_value,in_list_attr) ")
                append("RETURNING id)")
            }

            // Handle children and lists
            for ((index, attribute) in attributes.withIndex()) {
                // Advance to children
                if (attribute.children.isNotEmpty())
                    addAttributes(attribute.children.values, myTableNumber, index, false)

                // Handle list
                if (attribute is ListAttr && attribute.value.isNotEmpty())
                    addAttributes(attribute.value, myTableNumber, index, false, true)
            }
        }

        addAttributes(attributes)
    }

    protected fun writeTypedAttribute(attribute: Attribute<*>, type: KClass<*>, to: SQL, writeCast: Boolean) {
        val cast = if (writeCast) {
            when (type) {
                StringAttr::class -> ""
                IDAttr::class -> "::uuid"
                DateTimeAttr::class -> "::timestamptz"
                IntAttr::class -> "::bigint"
                BoolAttr::class -> "::boolean"
                RealAttr::class -> "::double precision"
                else -> throw UnsupportedOperationException("Unknown attribute type $type.")
            }
        } else ""
        if (type.isInstance(attribute)) {
            when (type) {
                IntAttr::class, BoolAttr::class -> to.sql.append("${attribute.value}$cast,")
                IDAttr::class -> to.sql.append("'${attribute.value}'$cast,")
                DateTimeAttr::class -> to.sql.append("'${ISO8601.format(attribute.value as Instant)}'$cast,")
                else -> {
                    to.sql.append("?$cast,")
                    to.params.addLast(attribute.value)
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

    private fun writeGlobals(scope: String, globals: Collection<Attribute<*>>, to: SQL) =
        writeAttributes("GLOBALS", "log", 0, globals, to, mapOf("scope" to scope))

    protected fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
        for (item in this@join) {
            append(", ")
            append(transform(item))
        }
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
