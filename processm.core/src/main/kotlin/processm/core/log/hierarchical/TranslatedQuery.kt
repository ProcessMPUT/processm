package processm.core.log.hierarchical

import processm.core.querylanguage.*
import processm.core.querylanguage.Function
import java.sql.Connection
import java.sql.ResultSet
import java.util.*

internal class TranslatedQuery(private val pql: Query) {
    companion object {
        private val queryLogClassifiers: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT
                scope,
                name,
                keys
            FROM
                classifiers
            WHERE
                log_id = ?
            ORDER BY id"""
            )
        }
        private val queryLogExtensions: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT
                name,
                prefix,
                uri
            FROM
                extensions
            WHERE
                log_id = ?
            ORDER BY id"""
            )
        }
        private val queryLogGlobals: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT
                id,
                parent_id,
                scope,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr 
            FROM
                globals
            WHERE
                log_id = ?
            ORDER BY id"""
            )
        }
    }

    private var connection: Connection? = null
    private val cache: Cache = Cache()
    private val snapshot: Snapshot by lazy(LazyThreadSafetyMode.NONE) { Snapshot() }

    // region SQL query generators
    /**
     * SQL query generators. They are lazy because:
     * * We aim to initialize the necessary ones only,
     * * We require connection context for some of them.
     */
    private val queryLogIds: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { idQuery(Scope.Log) }
    private val queryLogEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { entityQuery(Scope.Log) }
    private val queryLogAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { attributesQuery(Scope.Log) }
    private val queryLogExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { expressionsQuery(Scope.Log) }
    private val queryLogGroupEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupEntityQuery(Scope.Log) }
    private val queryLogGroupAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupAttributesQuery(Scope.Log) }
    private val queryLogGroupExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupExpressionsQuery(Scope.Log) }

    private val queryTraceIds: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { idQuery(Scope.Trace) }
    private val queryTraceEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { entityQuery(Scope.Trace) }
    private val queryTraceAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { attributesQuery(Scope.Trace) }
    private val queryTraceExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { expressionsQuery(Scope.Trace) }
    private val queryTraceGroupEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupEntityQuery(Scope.Trace) }
    private val queryTraceGroupAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupAttributesQuery(Scope.Trace) }
    private val queryTraceGroupExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupExpressionsQuery(Scope.Trace) }

    private val queryEventIds: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { idQuery(Scope.Event) }
    private val queryEventEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { entityQuery(Scope.Event) }
    private val queryEventAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { attributesQuery(Scope.Event) }
    private val queryEventExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { expressionsQuery(Scope.Event) }
    private val queryEventGroupEntity: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupEntityQuery(Scope.Event) }
    private val queryEventGroupAttributes: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupAttributesQuery(Scope.Event) }
    private val queryEventGroupExpressions: SQLQuery by lazy(LazyThreadSafetyMode.NONE) { groupExpressionsQuery(Scope.Event) }

    // region select ids
    private fun idQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        selectId(scope, it)

        val desiredFromPosition = it.query.length

        where(scope, it)
        orderBy(scope, it)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        offset(scope, it)
        limit(scope, it)
    }

    private fun selectId(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append("SELECT ${scope.alias}.id")
    }

    private fun where(scope: Scope, sql: MutableSQLQuery) {
        sql.scopes.add(ScopeWithHoisting(scope, 0))
        with(sql.query) {
            append(" WHERE ${scope.shortName}.id <= ")
            append(
                when (scope) {
                    Scope.Log -> snapshot.maxLogId
                    Scope.Trace -> snapshot.maxTraceId
                    Scope.Event -> snapshot.maxEventId
                }
            )

            if (pql.whereExpression == Expression.empty)
                return@with

            append(" AND ")
            pql.whereExpression.toSQL(sql)
        }
    }

    private fun orderBy(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" ORDER BY ")

            val expressions: List<OrderedExpression> = pql.orderByExpressions[scope]!!
            if (expressions.isEmpty()) {
                append("${scope.alias}.id")
                return@with
            }

            for (expression in expressions) {
                expression.base.toSQL(sql)
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
    private fun entityQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy)
        assert(pql.isGroupBy[Scope.Log] == false)

        selectEntity(Scope.Log, it)
        fromEntity(Scope.Log, it)
        whereEntity(Scope.Log, it)
    }

    private fun selectEntity(scope: Scope, sql: MutableSQLQuery, selectId: Boolean = true) {
        with(sql.query) {
            append("SELECT ")
            if (!pql.selectAll[scope]!! && pql.selectStandardAttributes[scope]!!.isEmpty()) {
                if (selectId)
                    append("${scope.shortName}.id")
                return@with
            }
            sql.scopes.add(ScopeWithHoisting(scope, 0))

            if (pql.selectAll[scope]!!) {
                append("${scope.shortName}.*")
                return@with
            }

            if (selectId)
                append("${scope.shortName}.id,")

            for (attribute in pql.selectStandardAttributes[scope]!!) {
                attribute.toSQL(sql)
                append(',')
            }
            deleteCharAt(length - 1)
        }
    }

    private fun fromEntity(scope: Scope, sql: MutableSQLQuery) {
        assert(sql.scopes.size == 1)
        assert(sql.scopes.first().scope == scope)
        sql.query.append(" FROM ${scope.table} ${scope.alias}")
    }

    private fun whereEntity(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append(" WHERE ${scope.alias}.id=?")
    }
    // endregion

    // region select attributes
    private fun attributesQuery(scope: Scope): SQLQuery = SQLQuery {
        selectAttributes(it)
        fromAttributes(scope, it)
        whereAttributes(scope, it)
        orderByAttributes(it)
    }

    private fun selectAttributes(sql: MutableSQLQuery) {
        sql.query.append(
            """SELECT
                id,
                parent_id,
                type,
                key,
                string_value,
                date_value,
                int_value,
                bool_value,
                real_value,
                in_list_attr"""
        )
    }

    private fun fromAttributes(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append(" FROM ${scope}s_attributes")
    }

    private fun whereAttributes(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" WHERE log_id=?")

            if (pql.selectAll[scope]!! || pql.selectOtherAttributes[scope]!!.isEmpty())
                return@with

            append(" AND parent_id IS NULL AND key=ANY(?)")
            sql.params.add(pql.selectOtherAttributes[scope]!!.toTypedArray())
        }
    }

    private fun orderByAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY id")
    }
    // endregion

    // region select expressions
    private fun expressionsQuery(scope: Scope): SQLQuery = SQLQuery {
        selectExpressions(scope, it)
        fromExpressions(scope, it)
        whereExpressions(scope, it)
        orderByExpressions(scope, it)
    }

    private fun selectExpressions(scope: Scope, sql: MutableSQLQuery) {
        TODO()
    }

    private fun fromExpressions(scope: Scope, sql: MutableSQLQuery) {
        TODO()
    }

    private fun whereExpressions(scope: Scope, sql: MutableSQLQuery) {
        TODO()
    }

    private fun orderByExpressions(scope: Scope, sql: MutableSQLQuery) {
        TODO()
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

        selectEntity(scope, it, false)

        val desiredFromPosition = it.query.length

        where(scope, it)
        groupBy(scope, it)
        orderBy(scope, it)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        offset(scope, it)
        limit(scope, it)
    }

    private fun groupBy(scope: Scope, sql: MutableSQLQuery) {
        val upperScopeGroupBy = pql.isImplicitGroupBy
                || scope > Scope.Log && pql.isGroupBy[Scope.Log] == true
                || scope > Scope.Trace && pql.isGroupBy[Scope.Trace] == true

        assert(upperScopeGroupBy || pql.isGroupBy[scope]!!)

        with(sql.query) {
            append(" GROUP BY ")
            for (attribute in pql.groupByStandardAttributes[scope]!!) {
                assert(attribute.isStandard)
                if (attribute.isClassifier)
                    TODO("fetch classifier definition")
                else
                    append("${attribute.scope!!.alias}.\"${attribute.standardName}\",")
            }
            for (attribute in pql.groupByOtherAttributes[scope]!!) {
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
    private fun groupAttributesQuery(scope: Scope): SQLQuery = SQLQuery {
        TODO()
    }
    // endregion

    // region select group expressions
    private fun groupExpressionsQuery(scope: Scope): SQLQuery = SQLQuery {
        TODO()
    }
    // endregion

    // endregion

    // region Public interface
    fun getLogs(): Executor<LogQueryResult> = LogExecutor()
    fun getTraces(logId: Int): Executor<QueryResult> = TraceExecutor(logId)
    fun getEvents(logId: Int, traceId: Long): Executor<QueryResult> = EventExecutor(logId, traceId)

    abstract inner class Executor<out T : QueryResult> {
        protected abstract val iterator: Iterator<T>

        fun use(connection: Connection, lambda: (iterator: Iterator<T>) -> Unit) {
            check(this@TranslatedQuery.connection === null)
            try {
                this@TranslatedQuery.connection = connection
                lambda(iterator)
            } finally {
                this@TranslatedQuery.connection = null
            }
        }

        protected fun <N : Number> ResultSet.toIdList(): List<N> = sequence {
            while (this@toIdList.next()) {
                yield(this@toIdList.getObject(1) as N)
            }
        }.toList()
    }

    private inner class LogExecutor : Executor<LogQueryResult>() {
        override val iterator: Iterator<LogQueryResult> = sequence {
            checkNotNull(connection)
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy && pql.isGroupBy[Scope.Log] == false

            if (regular) {
                // fetch and store log ids
                val ids = cache.getLogIds {
                    queryLogIds.execute(connection!!).toIdList<Int>()
                }

                // get and execute query
                for (id in ids) {
                    val parameters = listOf(id)
                    yield(
                        LogQueryResult(
                            queryLogEntity.execute(connection!!, parameters),
                            queryLogAttributes.execute(connection!!, parameters),
                            queryLogExpressions.execute(connection!!, parameters),
                            queryLogClassifiers.execute(connection!!, parameters),
                            queryLogExpressions.execute(connection!!, parameters),
                            queryLogGlobals.execute(connection!!, parameters)
                        )
                    )
                }
            } else {
                // get and execute query
                yield(
                    LogQueryResult(
                        queryLogGroupEntity.execute(connection!!),
                        queryLogGroupAttributes.execute(connection!!),
                        queryLogGroupExpressions.execute(connection!!)
                    )
                )
            }
        }.iterator()
    }

    private inner class TraceExecutor(logId: Int) : Executor<QueryResult>() {
        override val iterator: Iterator<QueryResult> = sequence {
            checkNotNull(connection)
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy
                    && pql.isGroupBy[Scope.Log] == false
                    && pql.isGroupBy[Scope.Trace] == false

            if (regular) {
                // fetch and store trace ids
                val ids = cache.getTraceIds(logId) {
                    queryTraceIds.execute(connection!!).toIdList<Long>()
                }

                // get and execute query
                for (id in ids) {
                    val parameters = listOf(id)
                    yield(
                        QueryResult(
                            queryTraceEntity.execute(connection!!, parameters),
                            queryTraceAttributes.execute(connection!!, parameters),
                            queryTraceExpressions.execute(connection!!, parameters)
                        )
                    )
                }
            } else {
                // get and execute query
                yield(
                    QueryResult(
                        queryTraceGroupEntity.execute(connection!!),
                        queryTraceGroupAttributes.execute(connection!!),
                        queryTraceGroupExpressions.execute(connection!!)
                    )
                )
            }
        }.iterator()
    }

    private inner class EventExecutor(logId: Int, traceId: Long) : Executor<QueryResult>() {
        override val iterator: Iterator<QueryResult> = sequence {
            checkNotNull(connection)
            // determine type of query: Regular or Grouped
            val regular = !pql.isImplicitGroupBy
                    && pql.isGroupBy[Scope.Log] == false
                    && pql.isGroupBy[Scope.Trace] == false
                    && pql.isGroupBy[Scope.Event] == false

            if (regular) {
                // fetch and store trace ids
                val ids = cache.getEventIds(logId, traceId) {
                    queryEventIds.execute(connection!!).toIdList<Long>()
                }

                // get and execute query
                for (id in ids) {
                    val parameters = listOf(id)
                    yield(
                        QueryResult(
                            queryEventEntity.execute(connection!!, parameters),
                            queryEventAttributes.execute(connection!!, parameters),
                            queryEventExpressions.execute(connection!!, parameters)
                        )
                    )
                }
            } else {
                // get and execute query
                yield(
                    QueryResult(
                        queryEventGroupEntity.execute(connection!!),
                        queryEventGroupAttributes.execute(connection!!),
                        queryEventGroupExpressions.execute(connection!!)
                    )
                )
            }
        }.iterator()
    }
    // endregion

    private inner class Snapshot {
        var maxLogId: Int = -1
            private set
        var maxTraceId: Long = -1L
            private set
        var maxEventId: Long = -1L
            private set

        init {
            checkNotNull(this@TranslatedQuery.connection)
            this@TranslatedQuery.connection!!
                .prepareStatement(
                    """SELECT 
                        MAX(l.id) AS max_log_id,
                        MAX(t.id) AS max_trace_id,
                        MAX(e.id) AS max_event_id
                    FROM logs l 
                    LEFT JOIN traces t ON l.id = t.log_id 
                    LEFT JOIN events e ON t.id = e.trace_id"""
                ).executeQuery()
                .use {
                    @Suppress("ComplexRedundantLet")
                    it.next().let { success -> assert(success) }
                    maxLogId = it.getInt("max_log_id")
                    maxTraceId = it.getLong("max_trace_id")
                    maxEventId = it.getLong("max_event_id")
                }
        }
    }

    private inner class Cache {
        /**
         * key: logId
         * value: cached information
         * The order is important.
         */
        private val entries = LinkedHashMap<Int, Entry>()

        fun expandClassifier(logId: Int, classifier: Attribute): List<Attribute> {
            val entry = entries[logId]

            checkNotNull(connection)
            checkNotNull(entry)

            return entry.classifiers.computeIfAbsent(classifier) {
                connection!!.prepareStatement(
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
                        .split(" ", "\t", "\r", "\n")
                        .map { Attribute("[${classifier.scope}:$it]", 0, 0) }
                }
            }
        }

        fun getLogIds(initializer: () -> List<Int>): Iterable<Int> {
            if (entries.isEmpty())
                entries.putAll(initializer().map { it to Entry() })
            return entries.keys
        }

        fun getTraceIds(logId: Int, initializer: () -> List<Long>): Iterable<Long> {
            val log = entries[logId]
            checkNotNull(log)
            if (log.ids.isEmpty())
                log.ids.putAll(initializer().map { it to ArrayList<Long>() })
            return log.ids.keys
        }

        fun getEventIds(logId: Int, traceId: Long, initializer: () -> List<Long>): Iterable<Long> {
            val log = entries[logId]
            checkNotNull(log)
            val trace = log.ids[traceId]
            checkNotNull(trace)
            if (trace.isEmpty())
                trace.addAll(initializer())
            return trace
        }

        private inner class Entry {
            val classifiers = HashMap<Attribute, List<Attribute>>()

            /**
             * traceId: \[eventId\]. The order is important.
             */
            val ids = LinkedHashMap<Long, ArrayList<Long>>()
        }
    }
}

private fun IExpression.toSQL(sql: MutableSQLQuery) {
    with(sql.query) {
        fun walk(expression: IExpression) {
            when (expression) {
                is Attribute -> {
                    val sqlScope = ScopeWithHoisting(expression.scope!!, expression.hoistingPrefix.length)
                    sql.scopes.add(sqlScope)
                    if (expression.isStandard) {
                        if (expression.scope == Scope.Log && expression.standardName == "db:id")
                            append("l.id") // for backward-compatibility with the previous implementation of the XES layer
                        else if (expression.isClassifier)
                            TODO()
                        else
                            append("${sqlScope.alias}.\"${expression.standardName}\"")
                    } else
                        TODO()
                }
                is NullLiteral -> append("null")
                is Literal<*> -> {
                    sql.params.add(expression.value!!)
                    append('?')
                }
                is Operator -> append(expression.value)
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