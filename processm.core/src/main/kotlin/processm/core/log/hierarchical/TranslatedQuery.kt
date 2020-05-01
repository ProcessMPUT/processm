package processm.core.log.hierarchical

import processm.core.helpers.NestableAutoCloseable
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.*
import processm.core.querylanguage.Function
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.collections.LinkedHashSet

@Suppress("MapGetWithNotNullAssertionOperator")
internal class TranslatedQuery(private val pql: Query, private val batchSize: Int = 1) {
    companion object {
        private val logger = logger()
        private val queryLogClassifiers: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT log_id, scope, name, keys
                FROM classifiers c
                JOIN unnest(?) WITH ORDINALITY ids(id, ord) ON c.log_id=ids.id
                ORDER BY ids.ord, c.id"""
            )
        }
        private val queryLogExtensions: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT log_id, name, prefix, uri
                FROM extensions e
                JOIN unnest(?) WITH ORDINALITY ids(id, ord) ON e.log_id=ids.id
                ORDER BY ids.ord, e.id"""
            )
        }
    }

    private val connection: NestableAutoCloseable<Connection> = NestableAutoCloseable {
        DBConnectionPool.getConnection().apply {
            assert(metaData.supportsMultipleResultSets())
            assert(metaData.supportsCorrelatedSubqueries())
            assert(metaData.supportsTransactions())
            assert(metaData.supportsUnionAll())
            assert(metaData.maxStatements == 0 || metaData.maxStatements >= 6)

            autoCommit = false
            prepareStatement("START TRANSACTION READ ONLY").execute()
        }
    }
    private val cache: Cache = Cache()

    // region SQL query generators
    /**
     * SQL query generators. They are lazy because:
     * * We aim to initialize the necessary ones only,
     * * We require connection context for some of them.
     */
    private val queryLogIds: SQLQuery by lazy(NONE) { idQuery(Scope.Log, null, null) }
    private val queryLogEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Log, null) }
    private val queryLogGlobals: SQLQuery by lazy(NONE) { attributesQuery(Scope.Log, null, "GLOBALS", setOf("scope")) }
    private val queryLogAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Log, null) }
    private val queryLogExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Log, null) }
    private val queryLogGroupEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Log, null, null) }
    private val queryLogGroupAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Log, null) }
    private val queryLogGroupExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Log, null) }

    // region select ids
    private fun idQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        selectId(scope, it)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        orderBy(scope, it, logId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        offset(scope, it)
        limit(scope, it)
    }

    private fun selectId(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append("SELECT ${scope.alias}.id")
    }

    private fun where(scope: Scope, sql: MutableSQLQuery, logId: Int?, traceId: Long?) {
        sql.scopes.add(ScopeWithHoisting(scope, 0))
        with(sql.query) {
            // filter by trace id and log id if necessary
            when (scope) {
                Scope.Trace -> append(" WHERE t.log_id=$logId")
                Scope.Event -> append(" WHERE e.trace_id=$traceId")
            }

            if (pql.whereExpression == Expression.empty)
                return@with

            append(if (scope == Scope.Log) " WHERE " else " AND ")

            pql.whereExpression.toSQL(sql, null)
        }
    }

    private fun orderBy(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        with(sql.query) {
            append(" ORDER BY ")

            val expressions: List<OrderedExpression> = pql.orderByExpressions[scope]!!
            if (expressions.isEmpty()) {
                append("${scope.alias}.id")
                return@with
            }

            for (expression in expressions) {
                expression.base.toSQL(sql, logId)
                if (expression.direction == OrderDirection.Descending)
                    append(" DESC")
                append(',')
            }
            deleteCharAt(length - 1)
        }
    }

    private fun from(baseScope: Scope, sql: MutableSQLQuery): String = buildString {
        fun path(from: Scope, to: ScopeWithHoisting) = sequence {
            var current = from
            while (current != to.scope) {
                current = if (from < to.scope) current.lower!! else current.upper!!
                yield(ScopeWithHoisting(current, 0))
            }
            if (to.hoisting > 0)
                yield(to)
        }

        fun sorted(s1: Scope, s2: Scope) = if (s1 < s2) s1 to s2 else s2 to s1

        val existingAliases = HashSet<String>()
        existingAliases.add(baseScope.alias)

        append(" FROM ${baseScope.table} ${baseScope.alias}")
        for (sqlScope in sql.scopes) {
            var prevScope = baseScope
            for (joinWith in path(baseScope, sqlScope)) {
                if (joinWith.alias in existingAliases)
                    continue

                existingAliases.add(joinWith.alias)
                append(" LEFT JOIN ${joinWith.table} ${joinWith.alias} ON ")
                if (joinWith.hoisting == 0) {
                    val (parent, child) = sorted(prevScope, joinWith.scope)
                    append("${parent.alias}.id = ${child.alias}.${parent}_id")
                } else {
                    assert(prevScope == joinWith.scope)
                    assert(prevScope != Scope.Log)
                    append("${prevScope.alias}.${prevScope.upper}_id = ${joinWith.alias}.${joinWith.scope.upper}_id")
                }
                prevScope = joinWith.scope
            }
        }
    }

    private fun offset(scope: Scope, sql: MutableSQLQuery) {
        val offset = pql.offset[scope] ?: return
        sql.query.append(" OFFSET $offset")
    }

    private fun limit(scope: Scope, sql: MutableSQLQuery) {
        val limit = pql.limit[scope] ?: return
        sql.query.append(" LIMIT $limit")
    }
    // endregion

    // region select entity
    private fun entityQuery(scope: Scope, logId: Int?): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)

        selectEntity(scope, it, logId)
        fromEntity(scope, it)
        orderByEntity(it)
    }

    private fun selectEntity(scope: Scope, sql: MutableSQLQuery, logId: Int?, selectId: Boolean = true) {
        with(sql.query) {
            append("SELECT ")

            sql.scopes.add(ScopeWithHoisting(scope, 0))

            if (pql.selectAll[scope]!!) {
                append("${scope.shortName}.*")
                return@with
            }

            if (selectId) {
                append("${scope.shortName}.id, ")
                when (scope) {
                    Scope.Trace -> append("event_stream, ")
                }
            }

            if (!pql.selectAll[scope]!! && pql.selectStandardAttributes[scope]!!.isEmpty()) {
                delete(length - 2, length)
                return@with
            }

            for (attribute in pql.selectStandardAttributes[scope]!!) {
                attribute.toSQL(sql, logId)
                append(',')
            }
            deleteCharAt(length - 1)
        }
    }

    private fun fromEntity(scope: Scope, sql: MutableSQLQuery) {
        assert(sql.scopes.size == 1)
        assert(sql.scopes.first().scope == scope)
        sql.query.append(" FROM ${scope.table} ${scope.alias}")
        sql.query.append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) USING (id)")
    }

    private fun orderByEntity(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY ids.ord")
    }
    // endregion

    // region select attributes
    private fun attributesQuery(
        scope: Scope,
        logId: Int?,
        table: String? = null,
        extraColumns: Set<String> = emptySet()
    ): SQLQuery = SQLQuery {
        val attrTable = table ?: (scope.toString() + "s_attributes")
        with(it.query) {
            append("WITH RECURSIVE tmp AS (")
            selectAttributes(scope, attrTable, extraColumns, it)
            append(", ARRAY[ids.ord, $attrTable.id] AS path")
            append(" FROM $attrTable")
            append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) ON ${scope}_id=ids.id")
            whereAttributes(scope, it, logId)
            append("UNION ALL ")
            selectAttributes(scope, attrTable, extraColumns, it)
            append(", path || $attrTable.id")
            append(" FROM $attrTable")
            append(" JOIN tmp ON $attrTable.parent_id=tmp.id")
            append(") ")
            selectAttributes(scope, "tmp", extraColumns, it)
            append(" FROM tmp")
            orderByAttributes(it)
        }
    }

    private fun selectAttributes(scope: Scope, table: String, extraColumns: Set<String>, sql: MutableSQLQuery) {
        sql.query.append(
            """SELECT $table.id, $table.${scope}_id, $table.parent_id, $table.type, $table.key,
            $table.string_value, $table.date_value, $table.int_value, $table.bool_value, $table.real_value, $table.in_list_attr
            ${extraColumns.join { "$table.$it" }}"""
        )
    }

    private fun whereAttributes(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        with(sql.query) {
            append(" WHERE parent_id IS NULL ")

            if (pql.selectAll[scope]!!)
                return@with

            val other = LinkedHashSet(pql.selectOtherAttributes[scope]!!)
            if (scope == Scope.Trace || scope == Scope.Event) {
                assert(logId !== null)
                for (attribute in pql.selectStandardAttributes[scope]!!) {
                    if (!attribute.isClassifier)
                        continue
                    for (inCls in cache.expandClassifier(logId!!, attribute)) {
                        if (!inCls.isStandard)
                            other.add(inCls)
                    }
                }
            }

            if (other.isEmpty())
                return@with

            append(" AND key=ANY(?)")
            sql.params.add(other.toTypedArray() /* copy */)
        }
    }

    private fun orderByAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY path")
    }
    // endregion

    // region select expressions
    private fun expressionsQuery(scope: Scope, logId: Int?): SQLQuery = SQLQuery {
        selectExpressions(scope, it)
        fromExpressions(scope, it)
        whereExpressions(scope, it)
        orderByExpressions(scope, it)
    }

    private fun selectExpressions(scope: Scope, sql: MutableSQLQuery) {
        //TODO()
        sql.query.append("SELECT * FROM unnest(?)")
    }

    private fun fromExpressions(scope: Scope, sql: MutableSQLQuery) {
        //TODO()
    }

    private fun whereExpressions(scope: Scope, sql: MutableSQLQuery) {
        //TODO()
    }

    private fun orderByExpressions(scope: Scope, sql: MutableSQLQuery) {
        //TODO()
    }
    // endregion

    // region select group entity
    private fun groupEntityQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(
            pql.isImplicitGroupBy
                    || pql.isGroupBy[Scope.Log]!!
                    || scope == Scope.Trace && pql.isGroupBy[Scope.Trace]!!
                    || scope == Scope.Event && pql.isGroupBy[Scope.Event]!!
        )

        selectEntity(scope, it, logId, false)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        groupBy(scope, it, logId)
        orderBy(scope, it, logId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        offset(scope, it)
        limit(scope, it)
    }

    private fun groupBy(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        val upperScopeGroupBy = pql.isImplicitGroupBy
                || scope > Scope.Log && pql.isGroupBy[Scope.Log] == true
                || scope > Scope.Trace && pql.isGroupBy[Scope.Trace] == true

        assert(upperScopeGroupBy || pql.isGroupBy[scope]!!)

        with(sql.query) {
            append(" GROUP BY ")

            // expand classifiers
            val standard = LinkedHashSet<Attribute>()
            val other = LinkedHashSet<Attribute>(pql.groupByOtherAttributes[scope]!!)
            pql.groupByStandardAttributes[scope]!!.forEach {
                if (it.isClassifier) {
                    assert(scope > Scope.Log) { "A classifier cannot occur on the log scope." }
                    assert(logId !== null) { "The use of a classifier requires logId. " }
                    cache.expandClassifier(logId!!, it).forEach {
                        if (it.isStandard) standard.add(it)
                        else other.add(it)
                    }
                } else
                    standard.add(it)
            }

            for (attribute in standard) {
                assert(attribute.isStandard)
                assert(!attribute.isClassifier)
                append("${attribute.scope!!.alias}.\"${attribute.standardName}\",")
            }

            for (attribute in other) {
                assert(!attribute.isStandard)
                assert(!attribute.isClassifier)
                TODO()
            }

            // remove the last comma
            deleteCharAt(length - 1)
        }
    }
    // endregion

    // region select group attributes
    private fun groupAttributesQuery(scope: Scope, logId: Int?): SQLQuery = SQLQuery {
        TODO("non-standard attributes")
    }
    // endregion

    // region select group expressions
    private fun groupExpressionsQuery(scope: Scope, logId: Int?): SQLQuery = SQLQuery {
        TODO("expressions")
    }
    // endregion

    // endregion

    // region Public interface
    fun getLogs(): Executor<LogQueryResult> = LogExecutor()
    fun getTraces(logId: Int): Executor<QueryResult> = TraceExecutor(logId)
    fun getEvents(logId: Int, traceId: Long): Executor<QueryResult> = EventExecutor(logId, traceId)

    abstract inner class Executor<T : QueryResult> {
        protected abstract val iterator: BaseIterator
        protected var connection: Connection? = null

        fun use(lambda: (iterator: Iterator<T>) -> Unit) {
            logger.enter()
            this@TranslatedQuery.connection.use {
                try {
                    // Internally leak and clear the reference to the connection to prevent incrementing the reference
                    // counter in the iterator sequences (as it would result in awkward constructions to clear references
                    // there).
                    connection = it
                    lambda(iterator)
                } finally {
                    connection = null
                }
            }
            logger.exit()
        }

        /**
         * @return true if more elements are available, false otherwise.
         */
        fun hasNext() = iterator.hasNext()

        /**
         * Skips the [batchSize] elements without connecting to the database.
         */
        fun skipBatch() = iterator.skip(batchSize)

        protected abstract inner class BaseIterator : Iterator<T> {
            abstract fun skip(count: Int)
        }

        protected abstract inner class IdBasedIterator<N : Number> : BaseIterator() {
            protected abstract val ids: Iterator<N>
            override fun hasNext(): Boolean = ids.hasNext()
            override fun skip(count: Int) {
                var index = 0
                while (index++ < count && ids.hasNext())
                    ids.next()
            }
        }

        protected inner class SingleValueIterator(var value: T?) : BaseIterator() {
            override fun hasNext(): Boolean = value !== null
            override fun skip(count: Int) {
                check(count > 0)
                value = null
            }

            override fun next(): T {
                val v = value!!
                value = null
                return v
            }
        }
    }

    private inner class LogExecutor : Executor<LogQueryResult>() {
        override val iterator: BaseIterator

        init {
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy && pql.isGroupBy[Scope.Log] == false
            if (regular) {
                iterator = object : IdBasedIterator<Int>() {
                    override val ids: Iterator<Int> = cache.getLogIds().iterator()

                    override fun next(): LogQueryResult {
                        val ids = this.ids.take(batchSize)
                        logger.trace { "Retrieving log ids: ${ids.joinToString()}." }
                        val parameters = listOf(connection!!.createArrayOf("int", ids.toTypedArray()))

                        val results = listOf(
                            queryLogEntity,
                            queryLogAttributes,
                            queryLogExpressions,
                            queryLogClassifiers,
                            queryLogExtensions,
                            queryLogGlobals
                        ).executeMany(
                            connection!!, parameters, parameters, parameters, parameters, parameters, parameters
                        )

                        return LogQueryResult(
                            ErrorSuppressingResultSet(results[0]),
                            results[1], results[2], results[3], results[4], results[5]
                        )
                    }
                }
            } else {
                val results = listOf(queryLogGroupEntity, queryLogGroupAttributes, queryLogGroupExpressions)
                    .executeMany(connection!!)
                iterator =
                    SingleValueIterator(LogQueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2]))
            }
        }
    }

    private inner class TraceExecutor(logId: Int) : Executor<QueryResult>() {
        override val iterator: BaseIterator

        init {
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy
                    && pql.isGroupBy[Scope.Log] == false
                    && pql.isGroupBy[Scope.Trace] == false
            val entry = cache[logId]
            if (regular) {
                iterator = object : IdBasedIterator<Long>() {
                    override val ids: Iterator<Long> = cache.getTraceIds(logId).iterator()

                    override fun next(): QueryResult {
                        val ids = this.ids.take(batchSize)
                        logger.trace { "Retrieving trace ids: ${ids.joinToString()}." }
                        val parameters = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))

                        val results = listOf(
                            entry.queryTraceEntity,
                            entry.queryTraceAttributes,
                            entry.queryTraceExpressions
                        ).executeMany(connection!!, parameters, parameters, parameters)

                        return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
                    }
                }

            } else {
                val results = listOf(
                    entry.queryTraceGroupEntity,
                    entry.queryTraceGroupAttributes,
                    entry.queryTraceGroupExpressions
                ).executeMany(connection!!)
                iterator =
                    SingleValueIterator(QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2]))
            }
        }
    }

    private inner class EventExecutor(logId: Int, traceId: Long) : Executor<QueryResult>() {
        override val iterator: BaseIterator

        init {
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy
                    && pql.isGroupBy[Scope.Log] == false
                    && pql.isGroupBy[Scope.Trace] == false
                    && pql.isGroupBy[Scope.Event] == false
            val entry = cache[logId].traces[traceId]!!
            if (regular) {
                iterator = object : IdBasedIterator<Long>() {
                    override val ids: Iterator<Long> = cache.getEventIds(logId, traceId).iterator()

                    override fun next(): QueryResult {
                        val ids = this.ids.take(batchSize)
                        logger.trace { "Retrieving event ids: $ids.joinToString()." }
                        val parameters = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))

                        val results = listOf(
                            entry.queryEventEntity,
                            entry.queryEventAttributes,
                            entry.queryEventExpressions
                        ).executeMany(connection!!, parameters, parameters, parameters)

                        return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
                    }
                }
            } else {
                val results = listOf(
                    entry.queryEventGroupEntity,
                    entry.queryEventGroupAttributes,
                    entry.queryEventGroupExpressions
                ).executeMany(connection!!)
                iterator =
                    SingleValueIterator(QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2]))
            }
        }
    }
    // endregion

    private inner class Cache {
        private var tracesInitialized: Boolean = false
        private var eventsInitialized: Boolean = false

        /**
         * key: logId
         * value: cached information
         * The order is important.
         */
        private val entries = LinkedHashMap<Int, LogEntry>()

        fun expandClassifier(logId: Int, classifier: Attribute): List<Attribute> {
            require(classifier.isClassifier)

            val entry = get(logId)
            return entry.classifiers.computeIfAbsent(classifier) {
                var attributes: List<Attribute>? = null
                connection.use { conn ->
                    attributes = conn.prepareStatement(
                        """SELECT
                    keys
                    FROM classifiers
                    WHERE
                        log_id=?
                        AND scope=?
                        AND name=?
                    LIMIT 1"""
                    ).apply {
                        setInt(1, logId)
                        setString(2, classifier.scope.toString())
                        setString(3, classifier.name)
                    }.executeQuery().use {
                        require(it.next()) { "Classifier $classifier is not found." }
                        it.getString("keys")
                    }.split(" ", "\t", "\r", "\n")
                        .map { Attribute("[${classifier.scope}:$it]", 0, 0) }
                }

                assert(attributes !== null)
                require(attributes!!.all { !it.isClassifier }) { "Line ${it.line} position ${it.charPositionInLine}: It is strictly forbidden for a classifier to refer other classifiers." }
                attributes!!
            }
        }

        fun getLogIds(): Iterable<Int> {
            if (entries.isEmpty()) {
                connection.use {
                    for (logId in queryLogIds.execute(it).toIdList<Int>())
                        entries[logId] = LogEntry(logId)
                }
            }

            return entries.keys
        }

        fun getTraceIds(logId: Int): Iterable<Long> {
            if (!tracesInitialized) {
                tracesInitialized = true

                connection.use {
                    val results = entries.values.map { it.queryTraceIds }.executeMany(it)
                    for ((index, lid) in entries.keys.withIndex())
                        for (traceId in results[index].toIdList<Long>())
                            entries[lid]!!.traces[traceId] = TraceEntry(lid, traceId)
                }
            }

            return get(logId).traces.keys
        }

        fun getEventIds(
            logId: Int,
            traceId: Long
        ): Iterable<Long> {
            val log = get(logId)
            if (!eventsInitialized) {
                eventsInitialized = true

                connection.use {
                    val results = log.traces.values.map { it.queryEventIds }.executeMany(it)
                    for ((index, tid) in log.traces.keys.withIndex())
                        for (eventId in results[index].toIdList<Long>())
                            log.traces[tid]!!.events.add(eventId)
                }
            }

            return checkNotNull(log.traces[traceId]).events
        }

        operator fun get(logId: Int): LogEntry =
            checkNotNull(entries[logId]) { "The cache entry for logId=$logId has not been initialized yet." }

        private fun <N : Number> ResultSet.toIdList(): List<N> = ArrayList<N>().also { out ->
            this@toIdList.use {
                while (it.next())
                    out.add(it.getObject(1) as N)
            }
        }


        inner class LogEntry(logId: Int) {
            val classifiers = HashMap<Attribute, List<Attribute>>()

            /**
             * The order is important.
             */
            val traces = LinkedHashMap<Long, TraceEntry>()

            val queryTraceIds: SQLQuery by lazy(NONE) { idQuery(Scope.Trace, logId, null) }
            val queryTraceEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Trace, logId) }
            val queryTraceAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Trace, logId) }
            val queryTraceExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Trace, logId) }

            val queryTraceGroupEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Trace, logId, null) }
            val queryTraceGroupAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Trace, logId) }
            val queryTraceGroupExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Trace, logId) }
        }

        inner class TraceEntry(logId: Int, traceId: Long) {
            /**
             * The order is important.
             */
            val events: MutableList<Long> = ArrayList()

            val queryEventIds: SQLQuery by lazy(NONE) { idQuery(Scope.Event, logId, traceId) }
            val queryEventEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Event, logId) }
            val queryEventAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Event, logId) }
            val queryEventExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Event, logId) }

            val queryEventGroupEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Event, logId, traceId) }
            val queryEventGroupAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Event, logId) }
            val queryEventGroupExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Event, logId) }
        }
    }

    private fun Iterable<Any>.join(transform: (a: Any) -> Any = { it }) = buildString {
        for (item in this@join) {
            append(", ")
            append(transform(item))
        }
    }

    private fun <T> Iterator<T>.take(limit: Int): List<T> {
        val list = ArrayList<T>(limit)
        while (list.size < limit && this.hasNext())
            list.add(this.next())
        return list
    }

    private fun IExpression.toSQL(sql: MutableSQLQuery, logId: Int?) {
        with(sql.query) {
            fun walk(expression: IExpression) {
                when (expression) {
                    is Attribute -> {
                        val sqlScope = ScopeWithHoisting(expression.scope!!, expression.hoistingPrefix.length)
                        sql.scopes.add(sqlScope)
                        // expand classifiers
                        val attributes =
                            if (expression.isClassifier) cache.expandClassifier(logId!!, expression)
                            else listOf(expression)

                        for (attribute in attributes) {
                            if (attribute.isStandard) {
                                if (attribute.scope == Scope.Log && attribute.standardName == "db:id")
                                    append("l.id") // for backward-compatibility with the previous implementation of the XES layer
                                else
                                    append("${sqlScope.alias}.\"${expression.standardName}\"")
                            } else
                                TODO("non-standard attribute")
                            append(',')
                        }
                        setLength(length - 1)
                    }
                    is NullLiteral -> append("null")
                    is Literal<*> -> {
                        sql.params.add(expression.value!!)
                        append('?')
                    }
                    is Operator -> append(" ${expression.value} ")
                    is Function -> {
                        append("${expression.name}(")
                        expression.children.forEach { walk(it); append(',') }
                        if (expression.children.isNotEmpty())
                            deleteCharAt(length - 1)
                        append(')')
                    }
                    // Expression must be the last but one because other classes inherit from this one.
                    is Expression -> expression.children.forEach { walk(it) }
                    else -> throw IllegalArgumentException("Unknown expression type: $expression")
                }
            }
            walk(this@toSQL)
        }
    }
}

