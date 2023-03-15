package processm.core.log.hierarchical

import processm.core.helpers.toUUID
import processm.core.log.*
import processm.core.log.attribute.*
import processm.core.log.attribute.Attribute.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.COST_CURRENCY
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.Attribute.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_GROUP
import processm.core.log.attribute.Attribute.ORG_RESOURCE
import processm.core.log.attribute.Attribute.ORG_ROLE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.Attribute.XES_FEATURES
import processm.core.log.attribute.Attribute.XES_VERSION
import processm.core.log.attribute.AttributeMap.Companion.LIST_TAG
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
 * * It is lazily-evaluated and it may keep [SoftReference]s to [XESComponent]s for fast reevaluation,
 * * Repeatable reads are ensured - each time this sequence is evaluated it yields [XESComponent]s with equal values of attributes,
 * * Phantom reads are prevented - each time this sequence is evaluated it yields equal [XESComponent]s,
 * * Repeatable reads may return the same objects or different but equal objects,
 * * The resulting view on [XESComponent]s is read-only,
 * * This class is not thread-safe: for concurrent evaluation one needs to use a synchronization mechanism.
 *
 * @property dbName A database's name - target database.
 * @property query An instance of a PQL query.
 * @property readNestedAttributes Control whether to retrieve the nested attributes.
 */
class DBHierarchicalXESInputStream(
    val dbName: String,
    val query: Query,
    val readNestedAttributes: Boolean = true
) : LogInputStream {
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
         * the same [XESComponent] objects during reevaluations.
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

        private const val logIdBitsInParentId: Int = 24
        private val traceIdBitsInParentId: Int = 63 - maxCachedBatchesPerParentLog2 - logIdBitsInParentId
        private val traceIdInParentIdMask: Long = (1L shl traceIdBitsInParentId) - 1L

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
    constructor(dbName: String, logId: Int) : this(dbName, Query(logId))

    private val translator = TranslatedQuery(dbName, query, batchSize, readNestedAttributes)

    /**
     * The key is a bitwise combination of three values:
     * * bit 63: the scope of the referenced object (0 for log or trace, 1 for event),
     * * bits 62-log2([maxCachedBatchesPerParent]): the primary key of the parent entity (all zeros for log, log id for trace,
     * (log id shl [traceIdBitsInParentId] or trace id) for event),
     * * bits (log2([maxCachedBatchesPerParent])-1)-0: the batch number.
     * The value is a [SoftReference] to a batch of the entities of the corresponding scope.
     *
     * Note that [ConcurrentHashMap] is used because the [cacheCleaner] runs in a separate thread.
     */
    private val cache: ConcurrentHashMap<Long, SoftReference<List<XESComponent>>> = ConcurrentHashMap()
    private fun <T : XESComponent> getCachedBatch(
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
            cache[key] = SoftReference<List<XESComponent>>(list)
            cacheCleaner.register(list) {
                cache.remove(key)
            }
        } else {
            skipAction()
        }

        @Suppress("UNCHECKED_CAST")
        return list as List<T>
    }

    override fun iterator(): Iterator<Log> = getLogs().iterator()

    private fun <T : QueryResult, R : XESComponent> get(
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
                if (traceId <= traceIdInParentIdMask) (logId.toLong() shl traceIdBitsInParentId) or traceId else maxParentIdInCache,
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
            xesVersion = result.entity.getString(XES_VERSION)
            xesFeatures = result.entity.getString(XES_FEATURES)
            conceptName = result.entity.getString(CONCEPT_NAME) ?: conceptName
            identityId = result.entity.getString(IDENTITY_ID).toUUID() ?: identityId
            lifecycleModel = result.entity.getString(LIFECYCLE_MODEL) ?: lifecycleModel
            count = result.entity.getIntOrNull("count") ?: 1

            setCustomAttributes(nameMap)

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
            val map = when (scope) {
                "event" -> log.eventGlobalsInternal
                "trace" -> log.traceGlobalsInternal
                else -> throw IllegalStateException("Illegal scope $scope for the global.")
            }
            readRecordsIntoAttributes(resultSet, map)
        }
    }

    private fun readExpressions(resultSet: ResultSet, component: XESComponent, id: Long) {
        if (resultSet.isBeforeFirst)
            resultSet.next()

        assert(resultSet.findColumn("id") == 1)
        if (resultSet.isEnded || resultSet.getLong(1) != id)
            return

        val expressions = query.selectExpressions[component.scope]!!
        val metadata = resultSet.metaData
        for (colIndex in 2..metadata.columnCount) {
            // TODO: replace with the expression label when included in the PQL specification
            val colName = expressions[colIndex - 2].toString()
            component.attributesInternal.computeIfAbsent(colName) {
                when (val colType = metadata.getColumnType(colIndex)) {
                    Types.VARCHAR, Types.NVARCHAR, Types.CHAR, Types.NCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR ->
                        resultSet.getString(colIndex)

                    Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT ->
                        resultSet.getLongOrNull(colIndex)

                    Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL, Types.DECIMAL ->
                        resultSet.getDoubleOrNull(colIndex)

                    Types.TIMESTAMP_WITH_TIMEZONE, Types.TIMESTAMP, Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE ->
                        resultSet.getTimestamp(colIndex, gmtCalendar)?.toInstant()

                    Types.BIT, Types.BOOLEAN ->
                        resultSet.getBooleanOrNull(colIndex)

                    Types.NULL ->
                        null

                    else -> throw UnsupportedOperationException("Unsupported expression type $colType for expression $colName.")
                }
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
            conceptName = result.entity.getString(CONCEPT_NAME) ?: conceptName
            costCurrency = result.entity.getString(COST_CURRENCY) ?: costCurrency
            costTotal = result.entity.getDoubleOrNull(COST_TOTAL) ?: costTotal
            identityId = result.entity.getString(IDENTITY_ID).toUUID() ?: identityId
            isEventStream = result.entity.getBooleanOrNull("event_stream") ?: false
            count = result.entity.getIntOrNull("count") ?: 1

            setCustomAttributes(nameMap)

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
            conceptName = result.entity.getString(CONCEPT_NAME) ?: conceptName
            conceptInstance = result.entity.getString(CONCEPT_INSTANCE) ?: conceptInstance
            costTotal = result.entity.getDoubleOrNull(COST_TOTAL) ?: costTotal
            costCurrency = result.entity.getString(COST_CURRENCY) ?: costCurrency
            identityId = result.entity.getString(IDENTITY_ID).toUUID() ?: identityId
            lifecycleState = result.entity.getString(LIFECYCLE_STATE) ?: lifecycleState
            lifecycleTransition = result.entity.getString(LIFECYCLE_TRANSITION) ?: lifecycleTransition
            orgRole = result.entity.getString(ORG_ROLE) ?: orgRole
            orgGroup = result.entity.getString(ORG_GROUP) ?: orgGroup
            orgResource = result.entity.getString(ORG_RESOURCE) ?: orgResource
            timeTimestamp = result.entity.getTimestamp(TIME_TIMESTAMP, gmtCalendar)?.toInstant() ?: timeTimestamp
            count = result.entity.getIntOrNull("count") ?: 1

            setCustomAttributes(nameMap)

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
        component: XESComponent,
        elementId: Number,
        nameMap: Map<String, String>
    ) {
        if (resultSet.isBeforeFirst)
            resultSet.next()

        while (!resultSet.isEnded && getElementId(resultSet) == elementId) {
            readRecordsIntoAttributes(resultSet, component.attributesInternal)
        }

        component.setStandardAttributes(nameMap)
    }

    private fun readRecordsIntoAttributes(
        resultSet: ResultSet,
        parentStorage: MutableAttributeMap
    ) {
        val key = resultSet.getString("key")
        val attr = attributeFromRecord(resultSet)

        parentStorage.putFlat(key, attr)
        resultSet.next()
    }

    private fun attributeFromRecord(record: ResultSet): Any {
        with(record) {
            val type = getString("type")!!
            assert(type.length >= 2)
            return when (type[0]) {
                's' -> getString("string_value")
                'f' -> getDouble("real_value")
                'i' -> when (type[1]) {
                    'n' -> getLong("int_value")
                    'd' -> getString("uuid_value").toUUID()!!
                    else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
                }

                'd' -> getTimestamp("date_value", gmtCalendar).toInstant()
                'b' -> getBoolean("bool_value")
                'l' -> LIST_TAG
                else -> throw IllegalStateException("Invalid attribute type ${getString("type")} in the database.")
            }
        }
    }

    private val ResultSet.isEnded
        // https://stackoverflow.com/a/15750832
        get() = this.isAfterLast || !this.isBeforeFirst && this.row == 0

    private val XESComponent.scope: Scope
        get() = when (this) {
            is Event -> Scope.Event
            is Trace -> Scope.Trace
            is Log -> Scope.Log
            else -> throw IllegalArgumentException("Unknown type ${this::class.simpleName}.")
        }
}

@Deprecated("Class was renamed. Type alias is provided for backward-compatibility.", level = DeprecationLevel.ERROR)
typealias DatabaseHierarchicalXESInputStream = DBHierarchicalXESInputStream
