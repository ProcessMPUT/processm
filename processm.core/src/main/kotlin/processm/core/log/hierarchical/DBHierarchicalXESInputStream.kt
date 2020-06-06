package processm.core.log.hierarchical

import processm.core.log.*
import processm.core.log.attribute.*
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.querylanguage.Query
import processm.core.querylanguage.Scope
import java.lang.ref.Cleaner
import java.lang.ref.SoftReference
import java.sql.ResultSet
import java.sql.Types
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads a sequence of [Log]s from database, filtered by the given [Query]. Every [Log] contains a sequence
 * of traces associated in its property [Log.traces], and every [Trace] contains a sequence of events associated
 * with its property [Trace.events].
 * This implementation ensures certain guarantees:
 * * All sequences can be empty, but none of them is null,
 * * It is lazily-evaluated and it may keep [SoftReference]s to [XESElement]s for fast reevaluation,
 * * Repeatable reads are ensured - each time this sequence is evaluated it yields [XESElement]s with equal values of attributes,
 * * Phantom reads are prevented - each time this sequence is evaluated it yields equal [XESElement]s,
 * * Repeatable reads may return the same objects or different but equal objects,
 * * The resulting view on [XESElement]s is read-only,
 * * This class is not thread-safe: for concurrent evaluation one needs to use a synchronization mechanism.
 *
 * @property query An instance of a PQL query.
 */
class DBHierarchicalXESInputStream(val query: Query) : LogInputStream {
    companion object {
        private val logger = logger()
        private val gmtCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"))

        /**
         * The maximum number of logs, traces, and events fetched at once. This value must be carefully chosen as
         * increasing it may substantially increase the memory consumption. E.g., for [batchSize]=32, at most 32 logs
         * may be fetched at once, for each log at most 32 traces may be fetched, and for each trace at most 32 events
         * may be fetched, resulting in 32*32*32=32768 objects in the fetch pool. Assuming the average memory consumption
         * for each object is 128B (large collections of attributes may be expensive), then this sequence may consume
         * approximately 4MB of RAM.
         *
         * Note that the actual memory consumption may be smaller, as it depends on the behavior of the consumer of this
         * [DBHierarchicalXESInputStream] because the nested sequences are fetched lazily.
         * The actual memory consumption may also be larger, as reevaluations may bring more objects to the fetch pool.
         * However, this effect is to some extent alleviated by the [cache] of [SoftReference]s to batches, that may use
         * the same [XESElement] objects during reevaluations.
         */
        private const val batchSize: Int = 32

        /**
         * The number of cacheable batches of children entities per parent entity. For [Log] it refers to the total number
         * of cacheable batches of logs. It must be a power of two greater than or equal 2. Increasing this number
         * decreases the range of parent ids allowed in cache to the ids less than 2**(63-log2[maxCachedBatchesPerParent]).
         * For a parent id out of this range, the batches for its children entities are not cached. The total number of
         * cached entities per parent id is upper bounded by [batchSize] * [maxCachedBatchesPerParent].
         */
        private const val maxCachedBatchesPerParent: Int = 16
        private val maxCachedBatchesPerParentLog2: Int = Integer.numberOfTrailingZeros(maxCachedBatchesPerParent)

        /**
         * An upper bound (exclusive) on the maximal cacheable parent id.
         */
        private val maxParentIdInCache: Long = 1L shl (63 - maxCachedBatchesPerParentLog2)

        private val cacheCleaner: Cleaner = Cleaner.create()

        init {
            assert(Integer.bitCount(maxCachedBatchesPerParent) == 1)
            assert(maxCachedBatchesPerParent >= 2)
        }
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

    /**
     * The key is a bitwise combination of three values:
     * * bit 63: the scope of the referenced object (0 for log or trace, 1 for event),
     * * bits 62-log2([maxCachedBatchesPerParent]): the primary key of the parent entity (all zeros for log, log id for trace, trace id for event),
     * * bits (log2([maxCachedBatchesPerParent])-1)-0: the batch number.
     * The value is a [SoftReference] to a batch of the entities of the corresponding scope.
     *
     * Note that [ConcurrentHashMap] is used because the [cacheCleaner] runs in a separate thread.
     */
    private val cache: ConcurrentHashMap<Long, SoftReference<List<XESElement>>> = ConcurrentHashMap()
    private fun <T : XESElement> getCachedBatch(
        scope: Scope,
        parentId: Long,
        batchIndex: Int,
        initializer: () -> List<T>,
        skipAction: () -> Unit
    ): List<T> {
        if (parentId >= maxParentIdInCache || batchIndex >= maxCachedBatchesPerParent)
            return initializer() // not cacheable

        assert(scope != Scope.Log || parentId == 0L)
        assert(scope != Scope.Trace || parentId != 0L)

        val key: Long = (if (scope == Scope.Event) Long.MIN_VALUE /* 0x8000000000000000UL */ else 0L) or
                (parentId shl maxCachedBatchesPerParentLog2) or
                batchIndex.toLong()
        var list = cache[key]?.get()
        if (list === null) {
            list = initializer()
            cache[key] = SoftReference<List<XESElement>>(list)
            cacheCleaner.register(list) {
                cache.remove(key)
            }
        } else {
            skipAction()
        }
        return list as List<T>
    }

    override fun iterator(): Iterator<Log> = getLogs().iterator()

    private fun <T : QueryResult, R : XESElement> get(
        getExecutor: () -> TranslatedQuery.Executor<T>,
        getBatch: (batchIndex: Int, initializer: () -> List<R>, skipAction: () -> Unit) -> List<R>,
        read: (result: T) -> R
    ): Sequence<R> = sequence {
        logger.enter()

        val executor = getExecutor()
        var batchIndex = 0
        while (executor.hasNext()) {
            val batch = getBatch(
                batchIndex++,
                {
                    val batch = ArrayList<R>(batchSize)
                    // execute query
                    executor.use {
                        val r = it.next()
                        while (r.entity.next())
                            batch.add(read(r))
                    } // close()
                    batch
                },
                executor::skipBatch
            )

            for (element in batch) {
                logger.trace { "Yielding $element" }
                yield(element)
            }
        }

        logger.exit()
    }

    private fun getLogs(): Sequence<Log> = get(
        translator::getLogs,
        { batchIndex, initializer, skipAction -> getCachedBatch(Scope.Log, 0L, batchIndex, initializer, skipAction) },
        ::readLog
    )

    private fun getTraces(logId: Int, nameMap: Map<String, String>): Sequence<Trace> = get(
        { translator.getTraces(logId) },
        { batchIndex, initializer, skipAction ->
            getCachedBatch(
                Scope.Trace,
                logId.toLong(),
                batchIndex,
                initializer,
                skipAction
            )
        },
        { readTrace(it, logId, nameMap) }
    )

    private fun getEvents(logId: Int, traceId: Long, nameMap: Map<String, String>): Sequence<Event> = get(
        { translator.getEvents(logId, traceId) },
        { batchIndex, initializer, skipAction ->
            getCachedBatch(
                Scope.Event,
                traceId,
                batchIndex,
                initializer,
                skipAction
            )
        },
        { readEvent(it, nameMap) }
    )

    private fun readLog(result: LogQueryResult): Log {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Log()) {
            val logId = result.entity.getInt("id")
            // Load classifiers, extensions, globals and attributes inside log structure
            readClassifiers(result.classifiers, this, logId)
            readExtensions(result.extensions, this, logId)
            readGlobals(result.globals, this, logId)
            val nameMap = this.extensions.values.getStandardToCustomNameMap()
            readLogAttributes(result.attributes, this, logId, nameMap)
            readExpressions(result.expressions, this, logId.toLong())

            // the standard attributes are to be read after all other attributes, as they may override the previous values
            xesVersion = result.entity.getString("xes:version")
            xesFeatures = result.entity.getString("xes:features")
            conceptName = result.entity.getString("concept:name") ?: conceptName
            identityId = result.entity.getString("identity:id") ?: identityId
            lifecycleModel = result.entity.getString("lifecycle:model") ?: lifecycleModel
            count = result.entity.getIntOrNull("count") ?: 1

            // getTraces is a sequence, so it will be actually called when one reads it
            traces = getTraces(logId, nameMap)

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

    private fun readExpressions(resultSet: ResultSet, element: XESElement, id: Long) {
        if (resultSet.isBeforeFirst)
            resultSet.next()

        assert(resultSet.findColumn("id") == 1)
        if (resultSet.isEnded || resultSet.getLong(1) != id)
            return

        val expressions = query.selectExpressions[element.scope]!!
        val metadata = resultSet.metaData
        for (colIndex in 2..metadata.columnCount) {
            // TODO: replace with the expression label when included in the PQL specification
            val colName = expressions[colIndex - 2].toString()
            element.attributesInternal.computeIfAbsent(colName) {
                when (val colType = metadata.getColumnType(colIndex)) {
                    Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                        resultSet.getString(colIndex)?.let { StringAttr(colName, it) }
                    Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
                        resultSet.getLongOrNull(colIndex)?.let { IntAttr(colName, it) }
                    Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
                        resultSet.getDoubleOrNull(colIndex)?.let { RealAttr(colName, it) }
                    Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP, Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE ->
                        resultSet.getTimestamp(colIndex, gmtCalendar)?.let { DateTimeAttr(colName, it.toInstant()) }
                    Types.BIT, Types.BOOLEAN ->
                        resultSet.getBooleanOrNull(colIndex)?.let { BoolAttr(colName, it) }
                    Types.NULL ->
                        NullAttr(colName)
                    else -> throw UnsupportedOperationException("Unsupported expression type $colType for expression $colName.")
                } ?: NullAttr(colName)
            }
        }

        resultSet.next() // move to the next row, if it exists then it should refer to the next entity
        assert(resultSet.isEnded || resultSet.getLong(1) != id)
    }


    private fun readTrace(result: QueryResult, logId: Int, nameMap: Map<String, String>): Trace {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Trace()) {
            val traceId = result.entity.getLong("id")
            readTraceAttributes(result.attributes, this, traceId, nameMap)
            readExpressions(result.expressions, this, traceId)

            // the standard attributes are to be read after all other attributes, as they may override the previous values
            conceptName = result.entity.getString("concept:name") ?: conceptName
            costCurrency = result.entity.getString("cost:currency") ?: costCurrency
            costTotal = result.entity.getDoubleOrNull("cost:total") ?: costTotal
            identityId = result.entity.getString("identity:id") ?: identityId
            isEventStream = result.entity.getBooleanOrNull("event_stream") ?: false
            count = result.entity.getIntOrNull("count") ?: 1

            // getEvents is a sequence, so it will be actually called when one reads it
            events = getEvents(logId, traceId, nameMap)

            logger.exit()
            return this
        }
    }

    private fun readEvent(result: QueryResult, nameMap: Map<String, String>): Event {
        logger.enter()

        assert(!result.entity.isEnded) { "By contract a row must exist." }

        with(Event()) {
            val eventId = result.entity.getLong("id")
            readEventAttributes(result.attributes, this, eventId, nameMap)
            readExpressions(result.expressions, this, eventId)

            // the standard attributes are to be read after all other attributes, as they may override the previous values
            conceptName = result.entity.getString("concept:name") ?: conceptName
            conceptInstance = result.entity.getString("concept:instance") ?: conceptInstance
            costTotal = result.entity.getDoubleOrNull("cost:total") ?: costTotal
            costCurrency = result.entity.getString("cost:currency") ?: costCurrency
            identityId = result.entity.getString("identity:id") ?: identityId
            lifecycleState = result.entity.getString("lifecycle:state") ?: lifecycleState
            lifecycleTransition = result.entity.getString("lifecycle:transition") ?: lifecycleTransition
            orgRole = result.entity.getString("org:role") ?: orgRole
            orgGroup = result.entity.getString("org:group") ?: orgGroup
            orgResource = result.entity.getString("org:resource") ?: orgResource
            timeTimestamp = result.entity.getTimestamp("time:timestamp", gmtCalendar)?.toInstant() ?: timeTimestamp
            count = result.entity.getIntOrNull("count") ?: 1

            logger.exit()
            return this
        }
    }

    private fun readLogAttributes(resultSet: ResultSet, log: Log, logId: Int, nameMap: Map<String, String>) =
        readAttributes(resultSet, { it.getInt("log_id") }, log, logId, nameMap)

    private fun readTraceAttributes(resultSet: ResultSet, trace: Trace, traceId: Long, nameMap: Map<String, String>) =
        readAttributes(resultSet, { it.getLong("trace_id") }, trace, traceId, nameMap)

    private fun readEventAttributes(resultSet: ResultSet, event: Event, eventId: Long, nameMap: Map<String, String>) =
        readAttributes(resultSet, { it.getLong("event_id") }, event, eventId, nameMap)

    private fun readAttributes(
        resultSet: ResultSet,
        getElementId: (ResultSet) -> Number,
        element: XESElement,
        elementId: Number,
        nameMap: Map<String, String>
    ) {
        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && getElementId(resultSet) == elementId) {
            with(readRecordsIntoAttributes(resultSet)) {
                element.attributesInternal[this.key] = this
            }
        }

        element.setStandardAttributes(nameMap)
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
                else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
            }
        }
    }

    private val ResultSet.isEnded
        // https://stackoverflow.com/a/15750832
        get() = this.isAfterLast || !this.isBeforeFirst && this.row == 0

    private val XESElement.scope: Scope
        get() = when (this) {
            is Event -> Scope.Event
            is Trace -> Scope.Trace
            is Log -> Scope.Log
            else -> throw IllegalArgumentException("Unknown type ${this::class.simpleName}.")
        }
}

@Deprecated("Class was renamed. Type alias is provided for backward-compatibility.")
typealias DatabaseHierarchicalXESInputStream = DBHierarchicalXESInputStream