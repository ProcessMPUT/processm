package processm.core.log.hierarchical

import processm.core.helpers.NestableAutoCloseable
import processm.core.helpers.mapToArray
import processm.core.helpers.toLongRange
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.*
import processm.core.querylanguage.Function
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
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

    /**
     * Current timestamp supplementing all calls to the now() function in [pql]. This is required to achieve consistency
     * across successive calls and reevaluation of [XESInputStream].
     * It is lazy-initialized because not all queries use timestamps and we want this value to be as fresh as possible.
     */
    private val now: Timestamp by lazy(NONE) { Timestamp.from(Instant.now()) }
    private val cache: Cache = Cache()

    // region SQL query generators
    // region select ids
    private fun idQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        selectId(scope, it)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        groupById(scope, it)
        orderBy(scope, it, logId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        limit(scope, it)
        offset(scope, it)
    }

    private fun selectId(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append("SELECT ${scope.alias}.id")
    }

    private fun where(scope: Scope, sql: MutableSQLQuery, logId: Int?, traceId: Long?) {
        sql.scopes.add(ScopeWithMetadata(scope, 0))
        with(sql.query) {
            // filter by trace id and log id if necessary
            when (scope) {
                Scope.Trace -> append(" WHERE t.log_id=$logId")
                Scope.Event -> append(" WHERE e.trace_id=$traceId")
            }

            if (pql.whereExpression == Expression.empty)
                return@with

            append(if (scope == Scope.Log) " WHERE " else " AND ")

            pql.whereExpression.toSQL(sql)
        }
    }

    private fun groupById(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append(" GROUP BY ${scope.alias}.id")
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
                if (expression.base is Attribute) expression.base.toSQL(sql, logId)
                else expression.base.toSQL(sql)
                if (expression.direction == OrderDirection.Descending)
                    append(" DESC")
                append(',')
            }
            setLength(length - 1)
        }
    }

    private fun path(from: Scope, to: ScopeWithMetadata) = sequence {
        var current = from
        while (current != to.scope) {
            current = if (from < to.scope) current.lower!! else current.upper!!
            yield(ScopeWithMetadata(current, 0))
        }
        if (to.hoisting > 0)
            yield(to)
    }

    private fun sorted(s1: Scope, s2: Scope) = if (s1 < s2) s1 to s2 else s2 to s1

    private fun from(baseScope: Scope, sql: MutableSQLQuery): String = buildString {
        val existingAliases = HashSet<String>()
        existingAliases.add(baseScope.alias)

        append(" FROM ${baseScope.table} ${baseScope.alias}")
        for (sqlScope in sql.scopes) {
            var prevScope = baseScope
            for (joinWith in path(baseScope, sqlScope)) {
                if (joinWith.alias in existingAliases) {
                    prevScope = joinWith.scope
                    continue
                }

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
    private fun entityQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)

        selectEntity(scope, it)
        fromEntity(scope, it)
        orderByEntity(it)
    }

    private fun selectEntity(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append("SELECT ")

            // field sql.scopes is used in assertions only in this query
            assert(sql.scopes.add(ScopeWithMetadata(scope, 0)))

            if (pql.selectAll[scope]!!) {
                append("${scope.shortName}.*")
                return@with
            }

            append("${scope.shortName}.id, ")
            if (scope == Scope.Trace)
                append("event_stream, ")

            var fetchAnyAttr = false
            for (attribute in pql.selectStandardAttributes[scope]!!) {
                if (attribute.isClassifier)
                    continue
                fetchAnyAttr = true
                append("${scope.alias}.\"${attribute.standardName}\", ")
            }
            setLength(length - 2)

            if (!fetchAnyAttr && scope != Scope.Trace) {
                // FIXME: potential optimization: for scope != Trace skip query, return in-memory ResultSet with ids only
            }
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
        if (pql.selectAll[scope] != true
            && pql.selectOtherAttributes[scope].isNullOrEmpty()
            && pql.selectStandardAttributes[scope]!!.none { a -> a.isClassifier }
        ) {
            // dummy query that returns no data
            it.query.append("SELECT NULL AS id WHERE cardinality(?)=-1")
            // FIXME: optimization: return empty in-memory ResultSet
            return@SQLQuery
        }

        val attrTable = table ?: (scope.toString() + "s_attributes")
        with(it.query) {
            append("WITH RECURSIVE tmp AS (")
            selectAttributes(scope, attrTable, extraColumns, it)
            append(", ARRAY[ids.ord, $attrTable.id] AS path")
            append(" FROM $attrTable")
            append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) ON ${scope}_id=ids.id")
            whereAttributes(scope, it, logId)
            append(" UNION ALL ")
            selectAttributes(scope, attrTable, extraColumns, it)
            append(", path || $attrTable.id")
            append(" FROM $attrTable")
            append(" JOIN tmp ON $attrTable.parent_id=tmp.id AND $attrTable.parent_id IS NOT NULL")
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
                for (attribute in pql.selectStandardAttributes[scope]!! + pql.selectOtherAttributes[scope]!!) {
                    if (!attribute.isClassifier)
                        continue
                    for (inCls in cache.expandClassifier(logId!!, attribute)) {
                        assert(!inCls.isStandard) {
                            "Classifier $attribute refers a standard attribute $inCls. Classifiers use attribute names " +
                                    "from the log, so they do not know the names of the standard attributes and cannot reference them."
                        }
                        other.add(inCls)
                    }
                }
            }

            if (other.isEmpty()) {
                // FIXME: potential optimization: skip query, return in-memory ResultSet with no data
                // return@with
            }

            append("AND key=ANY(?) ")
            sql.params.add(other.mapToArray { it.name })
        }
    }

    private fun orderByAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY path")
    }
    // endregion

    // region select expressions
    private fun expressionsQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        if (pql.selectExpressions[scope].isNullOrEmpty()) {
            // FIXME: optimization: return empty ResultSet here
            it.query.append("SELECT NULL FROM unnest(?)")
            return@SQLQuery
        }

        selectExpressions(scope, it)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        groupById(scope, it)
        orderByExpressions(scope, it)

        it.query.insert(desiredFromPosition, from(scope, it))

        limit(scope, it)
        offset(scope, it)
    }

    private fun selectExpressions(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append("SELECT ")
            assert(pql.selectExpressions[scope]!!.isNotEmpty())
            for (expression in pql.selectExpressions[scope]!!) {
                expression.toSQL(sql)
                append(", ")
            }
            setLength(length - 2)
        }
    }

    private fun orderByExpressions(scope: Scope, sql: MutableSQLQuery) {
        //TODO()
    }
    // endregion

    // region select groups
    private fun groupIdsQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(
            pql.isImplicitGroupBy
                    || pql.isGroupBy[Scope.Log]!!
                    || scope == Scope.Trace && pql.isGroupBy[Scope.Trace]!!
                    || scope == Scope.Event && pql.isGroupBy[Scope.Event]!!
        )

        selectGroupIds(scope, it)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        groupById(scope, it, logId)
        orderBy(scope, it, logId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        limit(scope, it)
        offset(scope, it)

    }

    private fun selectGroupIds(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append("SELECT row_number() AS group_id, array_agg(id) AS ids ")
    }

    private fun groupById(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        val upperScopeGroupBy = pql.isImplicitGroupBy
                || scope > Scope.Log && pql.isGroupBy[Scope.Log] == true
                || scope > Scope.Trace && pql.isGroupBy[Scope.Trace] == true

        assert(upperScopeGroupBy || pql.isGroupBy[scope]!!)

        with(sql.query) {
            append(" GROUP BY ")

            // expand classifiers
            val standard = LinkedHashSet<Attribute>()
            val other = LinkedHashSet<Attribute>(pql.groupByOtherAttributes[scope]!!)
            (pql.groupByStandardAttributes[scope]!! + pql.groupByOtherAttributes[scope]!!).forEach {
                if (it.isClassifier) {
                    assert(scope > Scope.Log) { "A classifier cannot occur on the log scope." }
                    assert(logId !== null) { "The use of a classifier requires logId. " }
                    cache.expandClassifier(logId!!, it).forEach { attr ->
                        assert(!attr.isStandard)
                        other.add(attr)
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
            setLength(length - 1)
        }
    }
    // endregion

    // region select group entity
    private fun groupEntityQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(
            pql.isImplicitGroupBy
                    || pql.isGroupBy[Scope.Log]!!
                    || scope == Scope.Trace && pql.isGroupBy[Scope.Trace]!!
                    || scope == Scope.Event && pql.isGroupBy[Scope.Event]!!
        )

        selectGroupEntity(scope, it)
        fromGroupEntity(scope, it)
        groupByGroupEntity(it)
        orderByGroupEntity(it)
    }

    private fun selectGroupEntity(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append("SELECT ")

            sql.scopes.add(ScopeWithMetadata(scope, 0))

            assert(pql.selectAll[scope] != true)

            append("row_number() AS group_id, ")

            var fetchAnyAttr = false
            for (attribute in pql.selectStandardAttributes[scope]!!) {
                if (attribute.isClassifier)
                    continue
                fetchAnyAttr = true
                append("${scope.alias}.\"${attribute.standardName}\", ")
            }
            setLength(length - 2)

            if (!fetchAnyAttr) {
                // FIXME: potential optimization: skip query, return in-memory ResultSet with ids only
            }
        }
    }

    private fun fromGroupEntity(scope: Scope, sql: MutableSQLQuery) {
        assert(sql.scopes.size == 1)
        assert(sql.scopes.first().scope == scope)
        sql.query.append(" FROM ${scope.table} ${scope.alias}")
        sql.query.append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope.alias}.id=ANY(ids.ids)")
    }

    private fun groupByGroupEntity(sql: MutableSQLQuery) {
        sql.query.append(" GROUP BY ids.ord")
    }

    private fun orderByGroupEntity(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY ids.ord")
    }
    // endregion

    // region select group attributes
    private fun groupAttributesQuery(
        scope: Scope,
        logId: Int?,
        table: String? = null,
        extraColumns: Set<String> = emptySet()
    ): SQLQuery = SQLQuery {
        if (pql.selectOtherAttributes[scope].isNullOrEmpty()
            && pql.selectStandardAttributes[scope]!!.none { a -> a.isClassifier }
        ) {
            // dummy query that returns no data
            it.query.append("SELECT NULL AS id WHERE cardinality(?)=-1")
            // FIXME: optimization: return empty in-memory ResultSet
            return@SQLQuery
        }

        val attrTable = table ?: (scope.toString() + "s_attributes")
        with(it.query) {
            selectAttributes(scope, attrTable, extraColumns, it)
            fromGroupAttributes(scope, attrTable, it)
            whereAttributes(scope, it, logId)
            groupByGroupAttributes(it)
            orderByGroupAttributes(it)
        }
    }

    private fun fromGroupAttributes(scope: Scope, attrTable: String, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" FROM $attrTable")
            append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope}_id=ANY(ids.ids)")
        }
    }

    private fun groupByGroupAttributes(sql: MutableSQLQuery) {
        sql.query.append(" GROUP BY ids.ord")
    }

    private fun orderByGroupAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY ids.ord")
    }

// endregion

    // region select group expressions
    private fun groupExpressionsQuery(scope: Scope, logId: Int?): SQLQuery = SQLQuery {
        // TODO
        selectExpressions(scope, it)
        //fromExpressions(scope, it)
        //whereExpressions(scope, it)
        orderByExpressions(scope, it)
    }
    // endregion

    // endregion

    // region Public interface
    fun getLogs(): Executor<LogQueryResult> = LogExecutor()
    fun getTraces(logId: Int): Executor<QueryResult> = TraceExecutor(logId)
    fun getEvents(logId: Int, traceId: Long): Executor<QueryResult> = EventExecutor(logId, traceId)

    abstract inner class Executor<T : QueryResult> {
        protected abstract val iterator: IdBasedIterator<*>
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

        protected abstract inner class IdBasedIterator<out N : Any> : Iterator<T> {
            protected abstract val ids: Iterator<N>
            override fun hasNext(): Boolean = ids.hasNext()
            fun skip(count: Int) {
                var index = 0
                while (index++ < count && ids.hasNext())
                    ids.next()
            }
        }
    }

    private inner class LogExecutor : Executor<LogQueryResult>() {
        override val iterator: IdBasedIterator<Int> = object : IdBasedIterator<Int>() {
            override val ids: Iterator<Int> = cache.topEntry.logs.keys.iterator()

            override fun next(): LogQueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving log/group ids: ${ids.joinToString()}." }
                val parameters = listOf(connection!!.createArrayOf("int", ids.toTypedArray()))
                val attrParameters = parameters + cache.topEntry.queryAttributes.params

                val results = listOf(
                    cache.topEntry.queryEntity,
                    cache.topEntry.queryAttributes,
                    cache.topEntry.queryExpressions,
                    queryLogClassifiers,
                    queryLogExtensions,
                    cache.topEntry.queryGlobals
                ).executeMany(
                    connection!!, parameters, attrParameters, parameters, parameters, parameters, parameters
                )

                return LogQueryResult(
                    ErrorSuppressingResultSet(results[0]),
                    results[1],
                    results[2],
                    results[3],
                    results[4],
                    results[5]
                )
            }
        }
    }

    private inner class TraceExecutor(logId: Int) : Executor<QueryResult>() {
        private val log = cache.topEntry.logs[logId]!!
        override val iterator: IdBasedIterator<Long> = object : IdBasedIterator<Long>() {
            override val ids: Iterator<Long> = log.traces.keys.iterator()

            override fun next(): QueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving trace/group ids: ${ids.joinToString()}." }
                val parameters: List<Any> = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))
                val attrParameters = parameters + log.queryAttributes.params

                val results = listOf(
                    log.queryEntity,
                    log.queryAttributes,
                    log.queryExpressions
                ).executeMany(connection!!, parameters, attrParameters, parameters)

                return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
            }
        }
    }

    private inner class EventExecutor(logId: Int, traceId: Long) : Executor<QueryResult>() {
        private val trace = cache.topEntry.logs[logId]!!.traces[traceId]!!
        override val iterator: IdBasedIterator<Long> = object : IdBasedIterator<Long>() {
            override val ids: Iterator<Long> = trace.events.iterator()

            override fun next(): QueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving event/group ids: ${ids.joinToString()}." }
                val parameters = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))
                val attrParameters = parameters + trace.queryAttributes.params

                val results = listOf(
                    trace.queryEntity,
                    trace.queryAttributes,
                    trace.queryExpressions
                ).executeMany(connection!!, parameters, attrParameters, parameters)

                return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
            }
        }
    }
    // endregion

    private inner class Cache {
        // region classifiers
        /**
         * key: logId
         * value: map of classifier -> list of attributes
         */
        private val classifiers: MutableMap<Int, MutableMap<Attribute, List<Attribute>>> = HashMap()

        fun expandClassifier(logId: Int, classifier: Attribute): List<Attribute> {
            require(classifier.isClassifier)

            // FIXME: optimization: move classifier expansion to database; each time a classifier is encountered run a subquery to fetch the names of the other attributes
            return classifiers.computeIfAbsent(logId) { HashMap() }.computeIfAbsent(classifier) {
                var attributes: List<Attribute>? = null
                connection.use { conn ->
                    attributes = conn.prepareStatement(
                        "SELECT keys FROM classifiers WHERE log_id=? AND scope=?::scope_type AND name=? LIMIT 1"
                    ).apply {
                        setInt(1, logId)
                        setString(2, classifier.scope.toString())
                        setString(3, classifier.name.substringAfter("classifier:"))
                    }.executeQuery().use {
                        require(it.next()) { "Classifier $classifier is not found." }
                        it.getString("keys")
                    }.split(" ", "\t", "\r", "\n")
                        .map { Attribute("[${classifier.scope}:$it]", 0, 0) }
                }

                assert(attributes !== null)
                require(attributes!!.all { !it.isClassifier }) { "Line ${classifier.line} position ${classifier.charPositionInLine}: It is forbidden for a classifier to refer other classifiers." }
                attributes!!
            }
        }
        // endregion

        val topEntry: TopEntry by lazy(NONE) {
            when {
                !pql.isImplicitGroupBy && !pql.isGroupBy[Scope.Log]!! -> RegularTopEntry()
                !pql.isImplicitGroupBy && pql.isGroupBy[Scope.Log]!! -> GroupingTopEntry()
                pql.isImplicitGroupBy -> GroupedTopEntry()
                else -> throw IllegalArgumentException()
            }
        }

        abstract inner class Entry internal constructor() {
            abstract val queryIds: SQLQuery
            abstract val queryEntity: SQLQuery
            abstract val queryAttributes: SQLQuery
            abstract val queryExpressions: SQLQuery
        }

        abstract inner class TopEntry internal constructor() : Entry() {
            /**
             * key: logId or groupId
             * The order is important
             */
            abstract val logs: LinkedHashMap<Int, LogEntry>

            abstract val queryGlobals: SQLQuery
        }

        inner class RegularTopEntry internal constructor() : TopEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
            }

            override val logs: LinkedHashMap<Int, LogEntry> by lazy(NONE) {
                LinkedHashMap<Int, LogEntry>().apply {
                    connection.use {
                        val nextCtor = if (pql.isGroupBy[Scope.Log]!!) ::GroupingLogEntry else ::RegularLogEntry
                        for (logId in queryIds.execute(it).toIdList<Int>())
                            this[logId] = nextCtor(logId)
                    }
                }
            }

            override val queryIds: SQLQuery by lazy(NONE) { idQuery(Scope.Log, null, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Log) }
            override val queryGlobals: SQLQuery by lazy(NONE)
            { attributesQuery(Scope.Log, null, "GLOBALS", setOf("scope")) }
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Log, null) }
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Log, null, null) }
        }

        inner class GroupingTopEntry internal constructor() : TopEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(pql.isGroupBy[Scope.Log]!!)
            }

            override val logs: LinkedHashMap<Int, LogEntry> by lazy(NONE) {
                LinkedHashMap<Int, LogEntry>().apply {
                    connection.use {
                        groups = queryIds.execute(it).to2DArray()
                        for (groupId in groups.indices)
                            this[groupId] = GroupedLogEntry(groupId)
                    }
                }
            }

            /**
             * index: groupId
             * value: group of logIds
             * The order is important.
             */
            lateinit var groups: List<Array<Int>>
                private set

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Log, null, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Log) }
            override val queryGlobals: SQLQuery = SQLQuery { /* FIXME: return empty ResultSet for this query */ }
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Log, null) }
            override val queryExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Log, null) }
        }

        inner class GroupedTopEntry internal constructor() : TopEntry() {
            // There is only one group -> groupId=0
            init {
                assert(pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
            }

            override val logs: LinkedHashMap<Int, LogEntry>
                get() = TODO("Not yet implemented")

            override val queryIds: SQLQuery by lazy(NONE) { TODO() }
            override val queryEntity: SQLQuery by lazy(NONE) { TODO() }
            override val queryGlobals: SQLQuery = SQLQuery { /* FIXME: return empty ResultSet for this query */ }
            override val queryAttributes: SQLQuery by lazy(NONE) { TODO() }
            override val queryExpressions: SQLQuery by lazy(NONE) { TODO() }
        }

        abstract inner class LogEntry internal constructor() : Entry() {
            /**
             * key: traceId or groupId
             * The order is important.
             */
            abstract val traces: LinkedHashMap<Long, TraceEntry>
        }

        inner class RegularLogEntry internal constructor(logId: Int) : LogEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
            }

            override val traces: LinkedHashMap<Long, TraceEntry> by lazy(NONE) {
                LinkedHashMap<Long, TraceEntry>().apply {
                    connection.use {
                        val nextCtor = if (pql.isGroupBy[Scope.Trace]!!) ::GroupingTraceEntry else ::RegularTraceEntry
                        for (traceId in queryIds.execute(it).toIdList<Long>())
                            this[traceId] = nextCtor(logId, traceId)
                    }
                }
            }

            override val queryIds: SQLQuery by lazy(NONE) { idQuery(Scope.Trace, logId, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Trace) } // FIXME: this query is independent of logId, move it to the main class
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Trace, logId) }
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Trace, logId, null) }
        }

        inner class GroupingLogEntry internal constructor(logId: Int) : LogEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(pql.isGroupBy[Scope.Trace]!!)
            }

            override val traces: LinkedHashMap<Long, TraceEntry> by lazy(NONE) {
                LinkedHashMap<Long, TraceEntry>().apply {
                    connection.use {
                        groups = queryIds.execute(it).to2DArray()
                        for (groupId in groups.indices)
                            this[groupId.toLong()] = GroupedTraceEntry(logId, null, groupId.toLong())
                    }
                }
            }

            /**
             * index: groupId
             * value: group of traceIds
             * The order is important.
             */
            lateinit var groups: List<Array<Long>>
                private set

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Trace, logId, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Trace) } // FIXME: this query is independent of logId, move it to the main class
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Trace, logId) }
            override val queryExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Trace, logId) }
        }

        inner class GroupedLogEntry internal constructor(logGroupId: Int) : LogEntry() {
            init {
                assert(pql.isImplicitGroupBy || pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
            }

            override val traces: LinkedHashMap<Long, TraceEntry>
                get() = TODO("Not yet implemented")

            override val queryIds: SQLQuery by lazy(NONE) { TODO() }
            override val queryEntity: SQLQuery by lazy(NONE) { TODO() }
            override val queryAttributes: SQLQuery by lazy(NONE) { TODO() }
            override val queryExpressions: SQLQuery by lazy(NONE) { TODO() }
        }

        abstract inner class TraceEntry internal constructor() : Entry() {
            /**
             * index: eventId or groupId
             * The order is important.
             */
            abstract val events: List<Long>
        }

        inner class RegularTraceEntry internal constructor(logId: Int, traceId: Long) : TraceEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
                assert(!pql.isGroupBy[Scope.Event]!!)
            }

            override val events: List<Long> by lazy(NONE) {
                lateinit var list: List<Long>
                connection.use {
                    list = queryIds.execute(it).toIdList()
                }
                list
            }

            override val queryIds: SQLQuery by lazy(NONE) { idQuery(Scope.Event, logId, traceId) }
            override val queryEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Event) } // FIXME: optimization: this query is independent of logId, move it to the main class
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Event, logId) }
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Event, logId, traceId) }
        }

        inner class GroupingTraceEntry internal constructor(logId: Int, traceId: Long) : TraceEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
                assert(pql.isGroupBy[Scope.Event]!!)
            }

            override val events: List<Long> by lazy(NONE) {
                connection.use {
                    groups = queryIds.execute(it).to2DArray()
                }
                groups.indices.toLongRange().toList() // FIXME: optimization: return range here
            }

            /**
             * index: groupId
             * value: group of eventIds
             * The order is important.
             */
            lateinit var groups: List<Array<Long>>

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Event, logId, traceId) }
            override val queryEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Event) } // FIXME: optimization: this query is independent of logId, move it to the main class
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Event, logId) }
            override val queryExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Event, logId) }
        }

        inner class GroupedTraceEntry internal constructor(logId: Int?, logGroupId: Int?, traceGroupId: Long) :
            TraceEntry() {
            init {
                assert(pql.isImplicitGroupBy || pql.isGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Trace]!!)
                assert(!pql.isGroupBy[Scope.Event]!!)
            }

            override val events: List<Long>
                get() = TODO("Not yet implemented")

            override val queryIds: SQLQuery by lazy(NONE) { TODO() }
            override val queryEntity: SQLQuery by lazy(NONE) { TODO() }
            override val queryAttributes: SQLQuery by lazy(NONE) { TODO() }
            override val queryExpressions: SQLQuery by lazy(NONE) { TODO() }
        }
    }

    private fun Attribute.toSQL(sql: MutableSQLQuery, logId: Int?) {
        val sqlScope = ScopeWithMetadata(this.scope!!, this.hoistingPrefix.length)
        sql.scopes.add(sqlScope)
        // expand classifiers
        val attributes = if (this.isClassifier) cache.expandClassifier(logId!!, this) else listOf(this)

        with(sql.query) {
            for (attribute in attributes) {
                assert(this@toSQL.scope == attribute.scope)

                if (attribute.isStandard) {
                    if (attribute.scope == Scope.Log && attribute.standardName == "db:id")
                        append("l.id") // for backward-compatibility with the previous implementation of the XES layer
                    else
                        append("${sqlScope.alias}.\"${attribute.standardName}\"")
                } else {
                    // use subquery for the non-standard attribute
                    append("(SELECT TODO_value ")
                    TODO("how to select the right column? I don't want to fetch the attribute to detect its type.")
                    append("FROM ${sqlScope.table}_attributes ")
                    append("WHERE ${sqlScope}_id=${sqlScope.alias}.id AND key=? AND parent_id IS NULL)")
                    sql.params.add(attribute.name)
                }
                append(',')
            }
            setLength(length - 1)
        }
    }

    private fun IExpression.toSQL(sql: MutableSQLQuery) {
        with(sql.query) {
            fun walk(expression: IExpression) {
                when (expression) {
                    is Attribute -> expression.toSQL(sql, null)
                    is Literal<*> -> {
                        if (expression.scope != null)
                            sql.scopes.add(ScopeWithMetadata(expression.scope!!, 0))

                        when (expression) {
                            is NullLiteral -> append("null")
                            is DateTimeLiteral -> {
                                append('?')
                                sql.params.add(Timestamp.from(expression.value))
                            }
                            else -> {
                                append('?')
                                sql.params.add(expression.value!!)
                            }
                        }
                    }
                    is Operator -> append(" ${expression.value} ")
                    is Function -> {
                        when (expression.name) {
                            "min", "max", "avg", "count", "sum", "round", "lower", "upper" -> {
                                assert(expression.children.size == 1)
                                append(expression.name)
                                append('(')
                                walk(expression.children[0])
                                // expression.children.forEach { walk(it); append(',') }
                                // if (expression.children.isNotEmpty())
                                //     deleteCharAt(length - 1)
                                append(')')
                            }
                            "date", "time" -> {
                                assert(expression.children.size == 1)
                                walk(expression.children[0])
                                append("::")
                                append(expression.name)
                            }
                            "year", "month", "day", "hour", "minute", "quarter" -> {
                                assert(expression.children.size == 1)
                                append("extract(")
                                append(expression.name)
                                append(" from ")
                                walk(expression.children[0])
                                append(')')
                            }
                            "second" -> {
                                assert(expression.children.size == 1)
                                append("floor(extract(second from ")
                                append(" from ")
                                walk(expression.children[0])
                                append("))")
                            }
                            "millisecond" -> {
                                assert(expression.children.size == 1)
                                append("extract(milliseconds from ") // note the plural form
                                walk(expression.children[0])
                                append(")%1000")
                            }
                            "dayofweek" -> {
                                assert(expression.children.size == 1)
                                append("extract(dow from ")
                                walk(expression.children[0])
                                append(")+1")
                            }
                            "now" -> {
                                assert(expression.children.isEmpty())
                                append("?::timestamptz")
                                sql.params.add(now)
                            }
                            else -> throw IllegalArgumentException("Undefined function ${expression.name}")
                        }

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
