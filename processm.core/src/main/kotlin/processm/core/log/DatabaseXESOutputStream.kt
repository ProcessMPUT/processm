package processm.core.log

import processm.core.log.attribute.*
import processm.core.persistence.DBConnectionPool
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

class DatabaseXESOutputStream : XESOutputStream {
    /**
     * Connection with the database
     */
    private val connection = DBConnectionPool.getConnection()
    /**
     * Log ID of inserted Log record
     */
    private var logId: Int? = null
    /**
     * Trace ID of inserted Trace record
     */
    private var traceId: Long? = null
    /**
     * Event ID of inserted Event record
     */
    private var eventId: Long? = null
    /**
     * Prepared query with insert new Log into database
     * As return we expect to receive LogId
     */
    private val logQuery =
        connection.prepareStatement("""INSERT INTO LOGS (features, "concept:name", "identity:id", "lifecycle:model") VALUES (?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new log's attribute into database
     */
    private val logAttributeQuery =
        connection.prepareStatement("""INSERT INTO LOGS_ATTRIBUTES (log_id, key, type, string_value, date_value, int_value, bool_value, real_value, parent_id, in_list_attr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new log's global into database
     */
    private val globalQuery =
        connection.prepareStatement("""INSERT INTO GLOBALS (log_id, key, type, string_value, date_value, int_value, bool_value, real_value, parent_id, in_list_attr, scope) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new log's classifier into database
     */
    private val classifierQuery =
        connection.prepareStatement("""INSERT INTO CLASSIFIERS (log_id, scope, name, keys) VALUES (?, ?, ?, ?)""")
    /**
     * Prepared query with insert new log's extension into database
     */
    private val extensionQuery =
        connection.prepareStatement("""INSERT INTO EXTENSIONS (log_id, name, prefix, uri) VALUES (?, ?, ?, ?)""")
    /**
     * Prepared query with insert new Trace into database
     * As return we expect to receive TraceId
     */
    private val traceQuery =
        connection.prepareStatement("""INSERT INTO TRACES (log_id, "concept:name", "cost:total", "cost:currency", "identity:id", event_stream) VALUES (?, ?, ?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new trace's attribute into database
     */
    private val traceAttributeQuery =
        connection.prepareStatement("""INSERT INTO TRACES_ATTRIBUTES (trace_id, key, type, string_value, date_value, int_value, bool_value, real_value, parent_id, in_list_attr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new Event into database
     * As return we expect to receive EventId
     */
    private val eventQuery =
        connection.prepareStatement("""INSERT INTO EVENTS (trace_id, "concept:name", "concept:instance", "cost:total", "cost:currency", "identity:id", "lifecycle:transition", "lifecycle:state", "org:resource", "org:role", "org:group", "time:timestamp") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID""")
    /**
     * Prepared query with insert new event's attribute into database
     */
    private val eventAttributeQuery =
        connection.prepareStatement("""INSERT INTO EVENTS_ATTRIBUTES (event_id, key, type, string_value, date_value, int_value, bool_value, real_value, parent_id, in_list_attr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING ID""")

    init {
        // Disable autoCommit on connection - we want to add whole XES log structure
        connection.autoCommit = false
    }

    /**
     * Write XES Element into the database
     */
    override fun write(element: XESElement) {
        when (element) {
            is Event -> {
                // We expect to already store Trace object in the database - if only Log added == we have event stream now
                if (traceId == null) {
                    val eventStreamTraceElement = Trace()
                    // Set trace as event stream - special boolean flag
                    eventStreamTraceElement.isEventStream = true
                    write(eventStreamTraceElement)
                }

                storeEvent(element)
                for (attribute in element.attributes.values) {
                    storeTraceEventAttributes(eventAttributeQuery, eventId!!, attribute)
                }
            }
            is Trace -> {
                // We expect to already store Log object in the database
                if (logId == null) {
                    throw IllegalStateException("Log ID not set. Can not add trace to the database")
                }

                storeTrace(element)
                for (attribute in element.attributes.values) {
                    storeTraceEventAttributes(traceAttributeQuery, traceId!!, attribute)
                }
            }
            is Log -> {
                storeLog(element)
                storeExtensions(element.extensions)
                storeClassifiers("event", element.eventClassifiers)
                storeClassifiers("trace", element.traceClassifiers)

                for (globalAttribute in element.eventGlobals.values) {
                    storeGlobals("event", globalAttribute)
                }
                for (globalAttribute in element.traceGlobals.values) {
                    storeGlobals("trace", globalAttribute)
                }

                for (attribute in element.attributes.values) {
                    storeLogAttributes(attribute)
                }
            }
            else ->
                throw IllegalArgumentException("Unsupported XESElement found. Expected 'Log', 'Trace' or 'Event' but received ${element.javaClass}")
        }
    }

    private fun storeTraceEventAttributes(
        query: PreparedStatement,
        refID: Long,
        attribute: Attribute<*>,
        parentId: Long? = null,
        insideListTag: Boolean? = null
    ) {
        var parentRecordID: Long? = parentId

        // If has parent - set it, otherwise set null value
        if (parentRecordID != null) {
            query.setLong(9, parentRecordID)
        } else {
            query.setNull(9, Types.NULL)
        }

        // If inside <list> - set it, otherwise set null value
        if (insideListTag == true) {
            query.setBoolean(10, insideListTag)
        } else {
            query.setNull(10, Types.NULL)
        }

        query.setLong(1, refID)
        setAttributeValueInQuery(query, attribute)

        if (query.execute() && query.resultSet.next()) {
            parentRecordID = query.resultSet.getLong(1)
            for (child in attribute.children.values) {
                storeTraceEventAttributes(query, refID, child, parentRecordID)
            }
        } else {
            throw IllegalStateException("Not received expected AttributeId from the database")
        }

        if (attribute is ListAttr) {
            for (attrInList in attribute.getValue()) {
                storeTraceEventAttributes(query, refID, attrInList, parentRecordID, insideListTag = true)
            }
        }
    }

    /**
     * Store Log's attributes
     */
    private fun storeLogAttributes(attribute: Attribute<*>, parentId: Int? = null, insideListTag: Boolean? = null) {
        var parentRecordID: Int? = parentId

        // If has parent - set it, otherwise set null value
        if (parentRecordID != null) {
            logAttributeQuery.setInt(9, parentRecordID)
        } else {
            logAttributeQuery.setNull(9, Types.NULL)
        }

        // If inside <list> - set it, otherwise set null value
        if (insideListTag == true) {
            logAttributeQuery.setBoolean(10, insideListTag)
        } else {
            logAttributeQuery.setNull(10, Types.NULL)
        }

        logAttributeQuery.setInt(1, logId!!)
        setAttributeValueInQuery(logAttributeQuery, attribute)

        // Execute query and fetch recordId - required to be able to add children
        if (logAttributeQuery.execute() && logAttributeQuery.resultSet.next()) {
            parentRecordID = logAttributeQuery.resultSet.getInt(1)
            for (child in attribute.children.values) {
                storeLogAttributes(child, parentRecordID)
            }
        } else {
            throw IllegalStateException("Not received expected AttributeId from the database")
        }

        // If list attribute store also ordered attributes from <values> tag
        if (attribute is ListAttr) {
            for (attrInList in attribute.getValue()) {
                storeLogAttributes(attrInList, parentRecordID, insideListTag = true)
            }
        }
    }

    /**
     * Store Log's element
     */
    private fun storeLog(element: Log) {
        logQuery.setString(1, element.features)
        logQuery.setString(2, element.conceptName)
        logQuery.setString(3, element.identityId)
        logQuery.setString(4, element.lifecycleModel)

        if (logQuery.execute() && logQuery.resultSet.next()) {
            logId = logQuery.resultSet.getInt(1)
        } else {
            throw IllegalStateException("Not received expected LogId from the database")
        }
    }

    /**
     * Store Log's classifiers assigned to scope 'event' or 'trace'
     */
    private fun storeClassifiers(scope: String, classifiers: Map<*, Classifier>) {
        for (classifier in classifiers.values) {
            classifierQuery.setInt(1, logId!!)
            classifierQuery.setString(2, scope)
            classifierQuery.setString(3, classifier.name)
            classifierQuery.setString(4, classifier.keys)

            classifierQuery.execute()
        }
    }

    /**
     * Store Log's extensions
     */
    private fun storeExtensions(extensions: Map<*, Extension>) {
        for (extension in extensions.values) {
            extensionQuery.setInt(1, logId!!)
            extensionQuery.setString(2, extension.name)
            extensionQuery.setString(3, extension.prefix)
            extensionQuery.setString(4, extension.uri)

            extensionQuery.execute()
        }
    }

    /**
     * Store Global assigned to Log element
     */
    private fun storeGlobals(
        scope: String,
        attribute: Attribute<*>,
        parentId: Int? = null,
        insideListTag: Boolean? = null
    ) {
        var parentRecordID: Int? = parentId

        // Set LogId and scope
        globalQuery.setInt(1, logId!!)
        globalQuery.setString(11, scope)

        // If has parent - set it, otherwise set null value
        if (parentRecordID != null) {
            globalQuery.setInt(9, parentRecordID)
        } else {
            globalQuery.setNull(9, Types.NULL)
        }

        // If inside <list> - set it, otherwise set null value
        if (insideListTag == true) {
            globalQuery.setBoolean(10, insideListTag)
        } else {
            globalQuery.setNull(10, Types.NULL)
        }

        // Set value attributes, key and type
        setAttributeValueInQuery(globalQuery, attribute)

        // Execute query and fetch recordId - required to be able to add children
        if (globalQuery.execute() && globalQuery.resultSet.next()) {
            parentRecordID = globalQuery.resultSet.getInt(1)

            // Store children attribute with this record as parent
            for (child in attribute.children.values) {
                storeGlobals(scope, child, parentRecordID)
            }
        } else {
            throw IllegalStateException("Not received expected ID from the database")
        }

        // If list attribute store also ordered attributes from <values> tag
        if (attribute is ListAttr) {
            for (attrInList in attribute.getValue()) {
                storeGlobals(scope, attrInList, parentRecordID, insideListTag = true)
            }
        }
    }

    /**
     * Set Attribute value in query
     *
     * Null for each field with *_value.
     * Based on attribute's type set correct XES tag and value.
     * Store also attribute's key.
     */
    private fun setAttributeValueInQuery(query: PreparedStatement, attribute: Attribute<*>) {
        for (index in 4..8)
            query.setNull(index, Types.NULL)

        query.setString(2, attribute.key)
        query.setString(3, attribute.xesTag)

        when (attribute) {
            is IntAttr -> {
                query.setLong(6, attribute.value)
            }
            is RealAttr -> {
                query.setDouble(8, attribute.value)
            }
            is StringAttr, is IDAttr -> {
                query.setString(4, attribute.value as String?)
            }
            is DateTimeAttr -> {
                query.setTimestamp(5, Timestamp(attribute.value.time))
            }
            is BoolAttr -> {
                query.setBoolean(7, attribute.value)
            }
            is ListAttr -> {
            }
        }
    }

    /**
     * Store Trace element in the database
     */
    private fun storeTrace(element: Trace) {
        traceQuery.setInt(1, logId!!)
        traceQuery.setString(2, element.conceptName)
        if (element.costTotal != null) {
            traceQuery.setDouble(3, element.costTotal!!)
        } else {
            traceQuery.setNull(3, Types.NULL)
        }
        traceQuery.setString(4, element.costCurrency)
        traceQuery.setString(5, element.identityId)
        traceQuery.setBoolean(6, element.isEventStream)

        if (traceQuery.execute() && traceQuery.resultSet.next()) {
            traceId = traceQuery.resultSet.getLong(1)
        } else {
            throw IllegalStateException("Not received expected TraceId from the database")
        }
    }

    /**
     * Store Event element in the database
     */
    private fun storeEvent(element: Event) {
        eventQuery.setLong(1, traceId!!)
        eventQuery.setString(2, element.conceptName)
        eventQuery.setString(3, element.conceptInstance)
        if (element.costTotal != null) {
            eventQuery.setDouble(4, element.costTotal!!)
        } else {
            eventQuery.setNull(4, Types.NULL)
        }
        eventQuery.setString(5, element.costCurrency)
        eventQuery.setString(6, element.identityId)
        eventQuery.setString(7, element.lifecycleTransition)
        eventQuery.setString(8, element.lifecycleState)
        eventQuery.setString(9, element.orgResource)
        eventQuery.setString(10, element.orgRole)
        eventQuery.setString(11, element.orgGroup)
        eventQuery.setTimestamp(12, element.timeTimestamp?.time?.let { Timestamp(it) })

        if (eventQuery.execute() && eventQuery.resultSet.next()) {
            eventId = eventQuery.resultSet.getLong(1)
        } else {
            throw IllegalStateException("Not received expected EventId from the database")
        }
    }

    /**
     * Commit and close connection with the database
     */
    override fun close() {
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
}