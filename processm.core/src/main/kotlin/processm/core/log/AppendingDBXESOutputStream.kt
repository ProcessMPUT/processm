package processm.core.log

import processm.core.log.attribute.*
import java.sql.Connection
import java.time.Instant
import java.util.*

/**
 * An output XES stream that appends traces/events to the existing event log. The event log must have the identity:id
 * attribute set for identification. To make appending to the existing traces possible, the traces must also have
 * the identity:id attribute set. The anonymous traces with identity:id unset will not be appended and instead a new
 * trace will be created.
 */
class AppendingDBXESOutputStream(connection: Connection) : DBXESOutputStream(connection) {
    override fun write(component: XESComponent) {
        if (component is Log) {
            val existingLogId = getLogId(component)
            if (existingLogId !== null && existingLogId != this.logId) {
                flushQueue(true) // flush events and traces from the previous log
                sawTrace = false // we must not refer to a trace from the previous log
                this.logId = existingLogId
            } else {
                super.write(component)
            }
        } else {
            super.write(component)
        }
    }

    private fun getLogId(log: Log): Int? {
        requireNotNull(log.identityId) { "The attribute identity:id must be set for the log." }
        connection.prepareStatement("SELECT id FROM logs WHERE \"identity:id\"=?::uuid").use { stmt ->
            stmt.setString(1, log.identityId.toString())
            val rs = stmt.executeQuery()
            if (rs.next())
                return rs.getInt(1)
            return null
        }
    }

    private fun rearrangeQueue() {
        val traces = LinkedHashMap<UUID, Pair<Trace, ArrayList<Event>>>()
        var last: Pair<Trace, ArrayList<Event>>? = null

        fun getRandomUUID(): UUID {
            var uuid: UUID
            do {
                uuid = UUID.randomUUID()
            } while (uuid in traces)
            return uuid
        }

        for (component in queue) {
            when (component) {
                is Event -> last!!.second.add(component)
                is Trace -> {
                    val identityId = component.identityId ?: getRandomUUID()
                    last = traces.computeIfAbsent(identityId) {
                        component to ArrayList<Event>()
                    }
                }
            }
        }

        queue.clear()
        for ((trace, events) in traces.values) {
            queue.add(trace)
            queue.addAll(events)
        }
    }

    override fun flushQueue(force: Boolean) {
        if (queue.isEmpty())
            return

        rearrangeQueue()

        assert(logId !== null)

        val traceIdentitySql = SQL()
        val traceInsertionSql = SQL()
        val eventSql = SQL()
        val attrSql = SQL()

        with(traceIdentitySql.sql) {
            append("WITH trace_identity AS (")
            append("""SELECT t.id, to_insert."identity:id"::uuid, to_insert.ord """)
            append("""FROM traces t RIGHT JOIN unnest(?) WITH ORDINALITY to_insert("identity:id", ord) """)
            append("""ON t."identity:id"=to_insert."identity:id"::uuid AND log_id=$logId """)
            append("ORDER BY to_insert.ord)")
        }

        with(traceInsertionSql.sql) {
            append(", trace_insertion AS (")
            append("""INSERT INTO traces(log_id,"concept:name","cost:total","cost:currency","identity:id",event_stream) """)
            append("""(SELECT to_insert.log_id,to_insert."concept:name",to_insert."cost:total"::double precision,to_insert."cost:currency",to_insert."identity:id"::uuid,to_insert.event_stream::boolean """)
            append("""FROM trace_identity JOIN (VALUES""")
        }

        with(eventSql.sql) {
            append(", event AS (")
            append("""INSERT INTO EVENTS(trace_id,"concept:name","concept:instance","cost:total","cost:currency","identity:id","lifecycle:transition","lifecycle:state","org:resource","org:role","org:group","time:timestamp") VALUES""")
        }

        val traceIdentityIds = ArrayList<String>()
        var lastEventIndex = -1
        var lastTraceIndex = -1
        for (component in queue) {
            when (component) {
                is Event -> {
                    check(lastTraceIndex >= 0) { "Trace must precede event in the queue." }
                    ++lastEventIndex
                    writeEventData(component, eventSql, lastTraceIndex)
                    writeAttributes("EVENTS_ATTRIBUTES", "event", lastEventIndex, component.attributes, attrSql)
                }
                is Trace -> {
                    if (traceInsertionSql.params.size + eventSql.params.size + attrSql.params.size >= paramSoftLimit) {
                        // #102: if the total number of parameters in an SQL query is too large, then DO NOT start new trace
                        break
                    }
                    ++lastTraceIndex
                    if (component.identityId !== null)
                        traceIdentityIds.add(component.identityId.toString())
                    writeTraceData(component, traceInsertionSql)
                    writeAttributes("TRACES_ATTRIBUTES", "trace", lastTraceIndex, component.attributes, attrSql)
                }
                else -> throw UnsupportedOperationException("Unexpected $component.")
            }
        }

        assert(lastTraceIndex >= 0)

        traceIdentitySql.params.add(traceIdentityIds.toArray())

        with(traceInsertionSql) {
            sql.delete(sql.length - 2, sql.length)
            sql.append(""") to_insert(log_id,"concept:name","cost:total","cost:currency","identity:id",event_stream) """)
            sql.append("""ON trace_identity."identity:id"=to_insert."identity:id"::uuid AND trace_identity.id IS NULL """)
            sql.append("""ORDER BY trace_identity.ord """)
            sql.append(""") RETURNING id,"identity:id")""")

            sql.append(", trace AS (")
            sql.append("""SELECT COALESCE(tid.id, tin.id) AS id, (tid.id IS NULL) AS new """)
            sql.append("""FROM trace_identity tid LEFT JOIN trace_insertion tin ON tid."identity:id"=tin."identity:id" """)
            sql.append(" ORDER BY tid.ord")
            sql.append(")")
        }

        with(eventSql.sql) {
            delete(length - 2, length)
            append(" RETURNING id,true AS new)")
        }

        with(traceIdentitySql) {
            sql.append(traceInsertionSql.sql)
            params.addAll(traceInsertionSql.params)

            if (lastEventIndex >= 0) {
                sql.append(eventSql.sql)
                params.addAll(eventSql.params)
            }

            sql.append(attrSql.sql)
            params.addAll(attrSql.params)
            execute()
        }

        clearQueue(lastEventIndex, lastTraceIndex, force)
    }

    private fun writeAttributes(
        destinationTable: String,
        rootTempTable: String,
        rootIndex: Int,
        attributes: AttributeMap,
        to: SQL,
        extraColumns: Map<String, String> = emptyMap()
    ) {
        if (attributes.isEmpty())
            return

        fun addAttributes(
            attributes: List<AttributeMap>,
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
                    "(SELECT root.id, a.key, a.type, " +
                            "a.string_value, a.uuid_value, a.date_value, a.int_value, a.bool_value, a.real_value, " +
                            "${if (topMost) "NULL" else "(SELECT id FROM attributes$parentTableNumber ORDER BY id LIMIT 1 OFFSET $parentRowIndex)"}, " +
                            "a.in_list_attr${extraColumns.values.join { "'$it'" }} FROM (VALUES "
                )
            }
            with(to) {
                var first = true
                for (attributeMap in attributes) {
                    for (attribute in attributeMap.flat) {
                        sql.append("(?,'${attribute.value.xesTag}'")
                        if (first)
                            sql.append("::attribute_type")
                        sql.append(',')
                        params.addLast(attribute.key)
                        writeTypedAttribute(attribute.value, String::class, to, first)
                        writeTypedAttribute(attribute.value, UUID::class, to, first)
                        writeTypedAttribute(attribute.value, Instant::class, to, first)
                        writeTypedAttribute(attribute.value, Long::class, to, first)
                        writeTypedAttribute(attribute.value, Boolean::class, to, first)
                        writeTypedAttribute(attribute.value, Double::class, to, first)
                        sql.append(inList)
                        if (first) {
                            sql.append("::boolean")
                            first = false
                        }
                        sql.append("),")
                    }
                }
                assert(!first)
            }

            with(to.sql) {
                deleteCharAt(length - 1)
                append(") a(key,type,string_value,uuid_value,date_value,int_value,bool_value,real_value,in_list_attr)")
                append(", (SELECT id,new FROM $rootTempTable ORDER BY id LIMIT 1 OFFSET $rootIndex) root")
                append(" WHERE root.new)")
                append("RETURNING id)")
            }

            // Handle children and lists
            var index = 0
            for (attributeMap in attributes) {
                for (attribute in attributeMap.flat) {
                    val (key, value) = attribute

                    // Handle list
                    if (value is List<*> && value.isNotEmpty()) {
                        addAttributes(value as List<AttributeMap>, myTableNumber, index, false, true)
                    }
                    index++
                }
            }
        }

        addAttributes(listOf(attributes))
    }
}
