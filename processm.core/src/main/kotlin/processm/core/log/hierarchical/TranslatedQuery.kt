package processm.core.log.hierarchical

import processm.core.helpers.NestableAutoCloseable
import processm.core.helpers.mapToArray
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

            val insertPos = length

            pql.whereExpression.toSQL(sql, scope, Type.Any)
            if (length != insertPos) {
                insert(insertPos, if (scope == Scope.Log) " WHERE (" else " AND (")
                append(')')
            }
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
                append('1')
                return@with
            }

            for (expression in expressions) {
                if (expression.base is Attribute) expression.base.toSQL(sql, logId, scope, Type.Any)
                else expression.base.toSQL(sql, scope, Type.Any)
                if (expression.direction == OrderDirection.Descending)
                    append(" DESC")
                append(',')
            }
            setLength(length - 1)
        }
    }

    private fun path(from: Scope, to: ScopeWithMetadata) = ArrayList<ScopeWithMetadata>(4).apply {
        assert(to.hoisting <= 2)
        var current = ScopeWithMetadata(from, 0)

        // phase 1: go to the effective "to" scope
        val effectiveTo = (0 until to.hoisting).fold(to.scope) { s, _ -> s.upper!! }
        while (current.scope != effectiveTo) {
            current =
                ScopeWithMetadata(if (current.scope < effectiveTo) current.scope.lower!! else current.scope.upper!!, 0)
            add(current)
        }

        // phase 2: go to the actual "to" scope
        // Scope.ordinal happens to match the maximum hoisting level
        assert(0 == Scope.Log.ordinal)
        assert(1 == Scope.Trace.ordinal)
        assert(2 == Scope.Event.ordinal)

        assert(current == to || to.hoisting > 0)
        while (current != to) {
            assert(current.scope < to.scope)
            current = current.scope.lower!!.let { ScopeWithMetadata(it, to.hoisting.coerceAtMost(it.ordinal)) }
            add(current)
        }

        // The longest possible path is from "event" to "^^event": event -> trace -> log -> ^trace -> ^^event.
        // The first path element is not yielded, so the maximum path length is 4.
        assert(size <= 4)
    }

    private fun sorted(s1: ScopeWithMetadata, s2: ScopeWithMetadata) =
        if (s1.scope < s2.scope) s1 to s2 else s2 to s1

    private fun from(baseScope: Scope, sql: MutableSQLQuery): String = buildString {
        val existingAliases = HashSet<String>()
        existingAliases.add(baseScope.alias)

        append(" FROM ${baseScope.table} ${baseScope.alias}")
        for (sqlScope in sql.scopes) {
            var prevScope = ScopeWithMetadata(baseScope, 0)
            for (joinWith in path(baseScope, sqlScope)) {
                if (joinWith.alias in existingAliases) {
                    prevScope = joinWith
                    continue
                }

                existingAliases.add(joinWith.alias)
                append(" LEFT JOIN ${joinWith.table} ${joinWith.alias} ON ")

                val (parent, child) = sorted(prevScope, joinWith)
                append("${parent.alias}.id = ${child.alias}.${parent.scope}_id")

                prevScope = joinWith
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
    private fun expressionsQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        if (pql.selectExpressions[scope].isNullOrEmpty()) {
            // FIXME: optimization: return empty ResultSet here
            it.query.append("SELECT NULL AS id FROM unnest(?) WHERE 0=1")
            return@SQLQuery
        }

        selectExpressions(scope, it, "id")
        it.query.append(from(scope, it))
        it.query.append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) ON ${scope.alias}.id=ids.id ")
        it.query.append(" GROUP BY ids.id, ids.ord")
        orderByEntity(it)
    }

    private fun selectExpressions(scope: Scope, sql: MutableSQLQuery, idCol: String) {
        with(sql.query) {
            append("SELECT ids.$idCol AS id, ")
            assert(pql.selectExpressions[scope]!!.isNotEmpty())
            for (expression in pql.selectExpressions[scope]!!) {
                expression.toSQL(sql, scope, Type.Any)
                append(", ")
            }
            setLength(length - 2)
        }
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
        groupBy(scope, it, logId)
        orderBy(scope, it, logId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        limit(scope, it)
        offset(scope, it)

    }

    private fun selectGroupIds(scope: Scope, sql: MutableSQLQuery) {
        //row_number() OVER () AS group_id,
        sql.query.append("SELECT array_agg(${scope.alias}.id) AS ids ")
    }

    private fun groupBy(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        val upperScopeGroupBy = pql.isImplicitGroupBy
                || scope > Scope.Log && pql.isGroupBy[Scope.Log] == true
                || scope > Scope.Trace && pql.isGroupBy[Scope.Trace] == true

        assert(upperScopeGroupBy || pql.isGroupBy[scope]!!)

        with(sql.query) {
            append(" GROUP BY ")
            for (attribute in pql.groupByStandardAttributes[scope]!!) {
                attribute.toSQL(sql, logId, scope, Type.Any)
                append(", ")
            }

            for (attribute in pql.groupByOtherAttributes[scope]!!) {
                attribute.toSQL(sql, logId, scope, Type.Any)
                append(", ")
            }

            // remove the last comma
            setLength(length - 2)
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
        groupByGroupEntity(scope, it)
        orderByGroupEntity(it)
    }

    private fun selectGroupEntity(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append("SELECT ")

            sql.scopes.add(ScopeWithMetadata(scope, 0))

            assert(pql.selectAll[scope] != true)

            append("row_number() OVER () AS id, ")

            var fetchAnyAttr = false
            for (attribute in pql.selectStandardAttributes[scope]!!) {
                if (attribute.isClassifier)
                    continue
                fetchAnyAttr = true
                attribute.toSQL(sql, null, scope, Type.Any)
                append(", ")
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
        sql.query.append(" JOIN (SELECT * FROM unnest_2d_1d(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope.alias}.id = ANY(ids.ids)")
    }

    private fun groupByGroupEntity(scope: Scope, sql: MutableSQLQuery) = with(sql.query) {
        append(" GROUP BY ids.ord, ")
        for (attribute in pql.selectStandardAttributes[scope]!!) {
            if (attribute.isClassifier)
                continue
            attribute.toSQL(sql, null, scope, Type.Any)
            append(", ")
        }
        setLength(length - 2)
    }

    private fun orderByGroupEntity(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY ids.ord")
    }
    // endregion

    // region select group attributes
    private fun groupAttributesQuery(
        scope: Scope,
        logId: Int?,
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

        val attrTable = scope.toString() + "s_attributes"
        selectAttributes(scope, attrTable, extraColumns, it)
        fromGroupAttributes(scope, it)
        whereAttributes(scope, it, logId)
        groupByGroupAttributes(it)
        orderByGroupAttributes(it)
    }

    private fun fromGroupAttributes(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" FROM ${scope}s_attributes")
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
        if (pql.selectExpressions[scope].isNullOrEmpty()) {
            // FIXME: optimization: return empty ResultSet here
            it.query.append("SELECT NULL AS id FROM unnest(?) WHERE 0=1")
            return@SQLQuery
        }
        selectExpressions(scope, it, "ord")
        fromGroupEntity(scope, it)
        it.query.append(" GROUP BY ids.ord")
        orderByEntity(it)
    }

    // endregion

    // endregion

    // region Public interface
    fun getLogs(): Executor<LogQueryResult> = LogExecutor()
    fun getTraces(logId: Int): Executor<QueryResult> = TraceExecutor(logId)
    fun getEvents(logId: Int, traceId: Long): Executor<QueryResult> = EventExecutor(logId, traceId)

    abstract inner class Executor<T : QueryResult> {
        protected abstract val iterator: IdBasedIterator
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

        protected abstract inner class IdBasedIterator : Iterator<T> {
            protected abstract val ids: Iterator<Any>
            override fun hasNext(): Boolean = ids.hasNext()
            fun skip(count: Int) {
                var index = 0
                while (index++ < count && ids.hasNext())
                    ids.next()
            }
        }
    }

    private inner class LogExecutor : Executor<LogQueryResult>() {
        override val iterator: IdBasedIterator = object : IdBasedIterator() {
            override val ids: Iterator<Any> = cache.topEntry.logs.keys.iterator()

            override fun next(): LogQueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving log/group ids: ${ids.joinToString()}." }
                val parameters = listOf(connection!!.createArrayOf("int", ids.toTypedArray()))
                val attrParameters = parameters + cache.topEntry.queryAttributes.params
                val exprParameters = cache.topEntry.queryExpressions.params + parameters

                val results = listOf(
                    cache.topEntry.queryEntity,
                    cache.topEntry.queryAttributes,
                    cache.topEntry.queryExpressions,
                    queryLogClassifiers,
                    queryLogExtensions,
                    cache.topEntry.queryGlobals
                ).executeMany(
                    connection!!, parameters, attrParameters, exprParameters, parameters, parameters, parameters
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
        override val iterator: IdBasedIterator = object : IdBasedIterator() {
            override val ids: Iterator<Any> = log.traces.keys.iterator()

            override fun next(): QueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving trace/group ids: ${ids.joinToString()}." }
                val parameters: List<Any> = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))
                val attrParameters = parameters + log.queryAttributes.params
                val exprParameters = log.queryExpressions.params + parameters

                val results = listOf(
                    log.queryEntity,
                    log.queryAttributes,
                    log.queryExpressions
                ).executeMany(connection!!, parameters, attrParameters, exprParameters)

                return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
            }
        }
    }

    private inner class EventExecutor(logId: Int, traceId: Long) : Executor<QueryResult>() {
        private val trace = cache.topEntry.logs[logId]!!.traces[traceId]!!
        override val iterator: IdBasedIterator = object : IdBasedIterator() {
            override val ids: Iterator<Any> = trace.events.iterator()

            override fun next(): QueryResult {
                val ids = this.ids.take(batchSize)
                logger.trace { "Retrieving event/group ids: ${ids.joinToString()}." }
                val parameters = listOf(connection!!.createArrayOf("bigint", ids.toTypedArray()))
                val attrParameters = parameters + trace.queryAttributes.params
                val exprParameters = trace.queryExpressions.params + parameters

                val results = listOf(
                    trace.queryEntity,
                    trace.queryAttributes,
                    trace.queryExpressions
                ).executeMany(connection!!, parameters, attrParameters, exprParameters)

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
            abstract val logs: LinkedHashMap<*, LogEntry>

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
                        val nextCtor = if (pql.isGroupBy[Scope.Trace]!!) ::GroupingLogEntry else ::RegularLogEntry
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
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Log) }
        }

        inner class GroupingTopEntry internal constructor() : TopEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(pql.isGroupBy[Scope.Log]!!)
            }

            /**
             * index: groupId
             * key: group of logIds
             * value: grouping LogEntry
             * The order is important.
             */
            override val logs: LinkedHashMap<IntArray, LogEntry> by lazy(NONE) {
                LinkedHashMap<IntArray, LogEntry>().apply {
                    connection.use {
                        for ((index, group) in queryIds.execute(it).to2DIntArray().withIndex())
                            this[group] = GroupedLogEntry(index)
                    }
                }
            }

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
            abstract val traces: LinkedHashMap<*, TraceEntry>
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
                        val nextCtor = if (pql.isGroupBy[Scope.Event]!!) ::GroupingTraceEntry else ::RegularTraceEntry
                        for (traceId in queryIds.execute(it).toIdList<Long>())
                            this[traceId] = nextCtor(logId, traceId)
                    }
                }
            }

            override val queryIds: SQLQuery by lazy(NONE) { idQuery(Scope.Trace, logId, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { entityQuery(Scope.Trace) } // FIXME: this query is independent of logId, move it to the main class
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Trace, logId) }
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Trace) }
        }

        inner class GroupingLogEntry internal constructor(logId: Int) : LogEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(pql.isGroupBy[Scope.Trace]!!)
            }

            /**
             * index: groupId
             * key: group of traceIds
             * value: grouping trace
             * The order is important.
             */
            override val traces: LinkedHashMap<LongArray, TraceEntry> by lazy(NONE) {
                LinkedHashMap<LongArray, TraceEntry>().apply {
                    connection.use {
                        for ((index, group) in queryIds.execute(it).to2DLongArray().withIndex())
                            this[group] = GroupedTraceEntry(logId, null, index.toLong())
                    }
                }
            }

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
            abstract val events: List<Any>
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
            override val queryExpressions: SQLQuery by lazy(NONE) { expressionsQuery(Scope.Event) }
        }

        inner class GroupingTraceEntry internal constructor(logId: Int, traceId: Long) : TraceEntry() {
            init {
                assert(!pql.isImplicitGroupBy)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
                assert(pql.isGroupBy[Scope.Event]!!)
            }

            /**
             * index: groupId
             * value: group of eventIds
             * The order is important.
             */
            override val events: List<LongArray> by lazy(NONE) {
                lateinit var list: List<LongArray>
                connection.use {
                    list = queryIds.execute(it).to2DLongArray()
                }
                list
            }

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

    private fun Attribute.toSQL(sql: MutableSQLQuery, logId: Int?, scope: Scope, expectedType: Type) {
        val sqlScope = ScopeWithMetadata(this.scope!!, this.hoistingPrefix.length)
        sql.scopes.add(sqlScope)
        // FIXME: move classifier expansion to database
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
                    append("(SELECT get_${scope}_attribute(ids.id, ?, ?::attribute_type, null::${expectedType.asDBType})")
                    sql.params.add(attribute.name)
                    sql.params.add(expectedType.asAttributeType)
                }
                append(',')
            }
            setLength(length - 1)
        }
    }

    private fun IExpression.toSQL(sql: MutableSQLQuery, scope: Scope, _expectedType: Type) {
        with(sql.query) {
            fun walk(expression: IExpression, expectedType: Type) {
                when (expression) {
                    is Attribute -> expression.toSQL(
                        sql, null, scope,
                        if (expression.type != Type.Unknown) expression.type else expectedType
                    )
                    is Literal<*> -> {
                        if (expression.scope != null)
                            sql.scopes.add(ScopeWithMetadata(expression.scope!!, 0))

                        when (expression) {
                            is NullLiteral -> append("null")
                            is DateTimeLiteral -> {
                                append("?::timestamptz")
                                sql.params.add(Timestamp.from(expression.value))
                            }
                            else -> {
                                append("?::${expression.type.asDBType}")
                                sql.params.add(expression.value!!)
                            }
                        }
                    }
                    is Operator -> {
                        when (expression.operatorType) {
                            OperatorType.Prefix -> {
                                assert(expression.children.size == 1)
                                append(" ${expression.value} ")
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                            }
                            OperatorType.Infix -> {
                                assert(expression.children.size >= 2)
                                append(' ')
                                for (i in expression.children.indices) {
                                    walk(expression.children[i], expression.expectedChildrenTypes[i])
                                    if (i < expression.children.size - 1) {
                                        when (expression.value) {
                                            "matches" -> append(" ~ ")
                                            else -> append(" ${expression.value} ")
                                        }
                                    }
                                }
                                append(' ')
                            }
                            OperatorType.Postfix -> {
                                assert(expression.children.size == 1)
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append(" ${expression.value} ")
                            }
                        }
                    }
                    is Function -> {
                        when (expression.name) {
                            "min", "max", "avg", "count", "sum", "round", "lower", "upper" -> {
                                assert(expression.children.size == 1)
                                append(expression.name)
                                append('(')
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                // expression.children.forEach { walk(it); append(',') }
                                // if (expression.children.isNotEmpty())
                                //     deleteCharAt(length - 1)
                                append(')')
                            }
                            "date", "time" -> {
                                assert(expression.children.size == 1)
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append("::")
                                append(expression.name)
                            }
                            "year", "month", "day", "hour", "minute", "quarter" -> {
                                assert(expression.children.size == 1)
                                append("extract(")
                                append(expression.name)
                                append(" from ")
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append(')')
                            }
                            "second" -> {
                                assert(expression.children.size == 1)
                                append("floor(extract(second from ")
                                append(" from ")
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append("))")
                            }
                            "millisecond" -> {
                                assert(expression.children.size == 1)
                                append("extract(milliseconds from ") // note the plural form
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append(")%1000")
                            }
                            "dayofweek" -> {
                                assert(expression.children.size == 1)
                                append("extract(dow from ")
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append(")+1")
                            }
                            "now" -> {
                                assert(expression.children.isEmpty())
                                append("?::timestamptz")
                                sql.params.add(now)
                            }
                            else -> throw IllegalArgumentException("Undefined function ${expression.name}.")
                        }

                    }
                    is AnyExpression -> append(expression.value)
                    // Expression must be the last but one because other classes inherit from this one.
                    is Expression -> expression.children.withIndex()
                        .forEach { walk(it.value, expression.expectedChildrenTypes[it.index]) }
                    else -> throw IllegalArgumentException("Unknown expression type: $expression")
                }
            }
            walk(this@toSQL, _expectedType)
        }
    }
}
