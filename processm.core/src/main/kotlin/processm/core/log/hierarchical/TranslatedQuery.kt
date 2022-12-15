package processm.core.log.hierarchical

import processm.core.helpers.LazyNestableAutoCloseable
import processm.core.helpers.mapToArray
import processm.core.log.attribute.Attribute.DB_ID
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.*
import processm.core.querylanguage.Function
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import kotlin.LazyThreadSafetyMode.NONE

@Suppress("MapGetWithNotNullAssertionOperator")
internal class TranslatedQuery(
    private val dbName: String,
    private val pql: Query,
    private val batchSize: Int = 1,
    private val readNestedAttributes: Boolean = true
) {
    companion object {
        private val logger = logger()
        internal val idOffsetPlaceholder = Any()
        internal val idPlaceholder = Any()
        private const val secondsPerDay: Int = 24 * 60 * 60
        private val queryLogClassifiers: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT log_id, scope, name, keys
                FROM classifiers c
                JOIN unnest(?) WITH ORDINALITY ids(id, ord) ON c.log_id=ids.id
                ORDER BY ids.ord, c.id"""
            )
            it.params.add(idPlaceholder)
        }
        private val queryLogExtensions: SQLQuery = SQLQuery {
            it.query.append(
                """SELECT log_id, name, prefix, uri
                FROM extensions e
                JOIN unnest(?) WITH ORDINALITY ids(id, ord) ON e.log_id=ids.id
                ORDER BY ids.ord, e.id"""
            )
            it.params.add(idPlaceholder)
        }
    }

    private val connection: LazyNestableAutoCloseable<Connection> = LazyNestableAutoCloseable {
        DBCache.get(dbName).getConnection().apply {
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
     * The current timestamp supplementing all calls to the now() function in [pql]. This is required to achieve consistency
     * across successive calls and reevaluation of [processm.core.log.XESInputStream].
     * It is eagerly-initialized to reflect the moment of query invoke.
     */
    private val now: Timestamp = Timestamp.from(Instant.now())
    private val cache: Cache = Cache()

    // region SQL query generators
    // region select ids
    private fun idQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy[scope]!!)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        val orderBy = pql.orderByExpressions[scope]!!

        selectId(scope, it, orderBy.isEmpty())

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)
        if (orderBy.isNotEmpty())
            groupById(scope, it)
        orderBy.toSQL(it, logId, "1", true, true)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        limit(scope, it)
        offset(scope, it)
    }

    private fun selectId(scope: Scope, sql: MutableSQLQuery, distinct: Boolean) {
        sql.query.append("SELECT ${if (distinct) "DISTINCT" else ""} ${scope.alias}.id")
    }

    private fun where(scope: Scope, sql: MutableSQLQuery, logId: Int?, traceId: Long?) {
        sql.scopes.add(ScopeWithMetadata(scope, 0))
        with(sql.query) {
            // filter by trace id and log id if necessary
            when (scope) {
                Scope.Event -> {
                    append(" WHERE e.trace_id=?")
                    sql.params.add(traceId!!)
                }

                Scope.Trace -> append(" WHERE t.log_id=$logId")
                Scope.Log -> Unit
            }

            if (pql.whereExpression == Expression.empty)
                return@with

            append(if (scope == Scope.Log) " WHERE (" else " AND (")
            pql.whereExpression.toSQL(sql, Type.Any)
            append(')')
        }
    }

    private fun groupById(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append(" GROUP BY ${scope.alias}.id")
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
        assert(!pql.isImplicitGroupBy[scope]!!)
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

            //var fetchAnyAttr = false
            for (attribute in pql.selectStandardAttributes[scope]!!) {
                if (attribute.isClassifier)
                    continue
                //fetchAnyAttr = true
                append("${scope.alias}.\"${attribute.standardName}\", ")
            }
            setLength(length - 2)

            //if (!fetchAnyAttr && scope != Scope.Trace) {
            // FIXME: potential optimization: for scope != Trace skip query, return in-memory ResultSet with ids only
            //}
        }
    }

    private fun fromEntity(scope: Scope, sql: MutableSQLQuery) {
        assert(sql.scopes.size == 1)
        assert(sql.scopes.first().scope == scope)
        sql.query.append(" FROM ${scope.table} ${scope.alias}")
        sql.query.append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) USING (id)")
        sql.params.add(idPlaceholder)
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
            it.query.append("SELECT NULL AS id WHERE 0=1")
            return@SQLQuery
        }

        val attrTable = table ?: (scope.toString() + "s_attributes")
        with(it.query) {
            selectAttributes(scope, attrTable, extraColumns, it)
            append(" FROM $attrTable")
            append(" JOIN unnest(?) WITH ORDINALITY ids(id, path) ON ${scope}_id=ids.id")
            it.params.add(idPlaceholder)
            whereAttributes(scope, it, logId)
            orderByAttributes(it)
        }
    }

    private fun selectAttributes(scope: Scope, table: String, extraColumns: Set<String>, sql: MutableSQLQuery) {
        sql.query.append(
            """SELECT $table.id, $table.${scope}_id, $table.type, $table.key,
            $table.string_value, $table.uuid_value, $table.date_value, $table.int_value, $table.bool_value, $table.real_value
            ${extraColumns.join { "$table.$it" }}"""
        )
    }

    private fun whereAttributes(scope: Scope, sql: MutableSQLQuery, logId: Int?) {
        with(sql.query) {
            append(" WHERE 1=1 ")

            if(!readNestedAttributes)
                append("AND NOT starts_with(key, '$SEPARATOR') ")

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

            //if (other.isEmpty()) {
            // FIXME: potential optimization: skip query, return in-memory ResultSet with no data
            //}

            append("AND key=ANY(?) ")
            sql.params.add(other.mapToArray(Attribute::name))
        }
    }

    private fun orderByAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY path")
    }
    // endregion

    // region select expressions
    private fun expressionsQuery(scope: Scope): SQLQuery = SQLQuery {
        assert(!pql.isImplicitGroupBy[scope]!!)
        assert(pql.isGroupBy[Scope.Log] == false)
        assert(scope < Scope.Trace || pql.isGroupBy[Scope.Trace] == false)
        assert(scope < Scope.Event || pql.isGroupBy[Scope.Event] == false)

        if (pql.selectExpressions[scope].isNullOrEmpty()) {
            // FIXME: optimization: return empty ResultSet here
            it.query.append("SELECT NULL AS id WHERE 0=1")
            return@SQLQuery
        }

        selectExpressions(scope, it, false)
        it.query.append(from(scope, it))
        it.query.append(" JOIN (SELECT * FROM unnest(?) WITH ORDINALITY LIMIT $batchSize) ids(id, ord) ON ${scope.alias}.id=ids.id ")
        it.params.add(idPlaceholder)
        orderByEntity(it)
    }

    private fun selectExpressions(scope: Scope, sql: MutableSQLQuery, ord: Boolean) {
        with(sql.query) {
            append("SELECT ids.")
            if (ord) {
                append("ord+?")
                sql.params.add(idOffsetPlaceholder)
            } else {
                append("id")
            }
            append(" AS id, ")

            assert(pql.selectExpressions[scope]!!.isNotEmpty())
            for (expression in pql.selectExpressions[scope]!!) {
                expression.toSQL(sql, Type.Any)
                append(", ")
            }
            setLength(length - 2)
        }
    }
    // endregion

    // region select groups of ids
    private fun groupIdsQuery(scope: Scope, logId: Int?, traceId: Long?): SQLQuery = SQLQuery {
        assert(
            pql.isImplicitGroupBy[scope]!!
                    || pql.isGroupBy[Scope.Log]!!
                    || scope == Scope.Trace && pql.isGroupBy[Scope.Trace]!!
                    || scope == Scope.Event && pql.isGroupBy[Scope.Event]!!
        )

        it.query.append("SELECT array_agg(id) AS ids FROM (")
        val (attributes, groupByAttributes) = selectInnerGroup(it, scope, logId, false)

        val desiredFromPosition = it.query.length

        where(scope, it, logId, traceId)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        groupById(scope, it)
        it.query.append(')')
        innerGroupSignature(it, attributes, groupByAttributes, false, scope)

        outerGroupBy(scope, it, attributes, groupByAttributes, logId, false)

        limit(scope, it)
        offset(scope, it)
    }
    // endregion

    // region select group entity
    private fun groupEntityQuery(scope: Scope): SQLQuery = SQLQuery {
        selectGroupEntity(scope, it)
        fromGroupEntity(scope, it)
        groupByGroupEntity(scope, it)
        orderByGroupEntity(it)
    }

    private fun selectGroupEntity(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append("SELECT ")

            sql.scopes.add(ScopeWithMetadata(scope, 0))

            append("ids.ord+? AS id, ")
            sql.params.add(idOffsetPlaceholder)

            assert(pql.selectAll[scope] == false)
            for (attribute in pql.selectStandardAttributes[scope]!!) {
                if (attribute.isClassifier)
                    continue
                attribute.toSQL(sql, null, Type.Any, Int.MAX_VALUE)
                append(", ")
            }

            append("COUNT(*) AS count")
        }
    }

    private fun fromGroupEntity(scope: Scope, sql: MutableSQLQuery) {
        assert(sql.scopes.size == 1)
        assert(sql.scopes.first().scope == scope)
        sql.query.append(" FROM ${scope.table} ${scope.alias}")
        sql.query.append(" JOIN (SELECT * FROM unnest_2d_1d(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope.alias}.id = ANY(ids.ids)")
        sql.params.add(idPlaceholder)
    }

    private fun groupByGroupEntity(scope: Scope, sql: MutableSQLQuery) = with(sql.query) {
        append(" GROUP BY ids.ord, ")
        for (attribute in pql.selectStandardAttributes[scope]!!) {
            if (attribute.isClassifier)
                continue
            attribute.toSQL(sql, null, Type.Any, Int.MAX_VALUE)
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
        logId: Int?
    ): SQLQuery = SQLQuery {
        if (pql.selectOtherAttributes[scope].isNullOrEmpty()
            && pql.selectStandardAttributes[scope]!!.none { a -> a.isClassifier }
            && pql.selectAll[scope] == false
        ) {
            // dummy query that returns no data
            it.query.append("SELECT NULL AS id WHERE 0=1")
            // FIXME: optimization: return empty in-memory ResultSet
            return@SQLQuery
        }

        selectGroupAttributes(scope, it)
        fromGroupAttributes(scope, it)
        whereAttributes(scope, it, logId)
        orderByGroupAttributes(it)
    }

    private fun selectGroupAttributes(scope: Scope, sql: MutableSQLQuery) {
        sql.query.append(
            """SELECT DISTINCT ON(ids.ord, a.key) ids.ord AS id, ids.ord+? AS ${scope}_id, 
            a.type, a.key, a.string_value, a.uuid_value, a.date_value, a.int_value, a.bool_value, a.real_value"""
        )
        sql.params.add(idOffsetPlaceholder)
    }

    private fun fromGroupAttributes(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" FROM ${scope}s_attributes a")
            append(" JOIN (SELECT * FROM unnest_2d_1d(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope}_id=ANY(ids.ids)")
            sql.params.add(idPlaceholder)
        }
    }

    private fun orderByGroupAttributes(sql: MutableSQLQuery) {
        sql.query.append(" ORDER BY ids.ord")
    }
    // endregion

    // region select group expressions
    private fun groupExpressionsQuery(scope: Scope): SQLQuery = SQLQuery {
        if (pql.selectExpressions[scope].isNullOrEmpty()) {
            // FIXME: optimization: return empty ResultSet here
            it.query.append("SELECT NULL AS id WHERE 0=1")
            return@SQLQuery
        }
        selectExpressions(scope, it, true)

        it.query.append(from(scope, it))
        it.query.append(" JOIN (SELECT * FROM unnest_2d_1d(?) WITH ORDINALITY LIMIT $batchSize) ids(ids, ord) ON ${scope.alias}.id = ANY(ids.ids)")
        it.params.add(idPlaceholder)

        groupByExpressions(scope, it)
        it.query.append(" ORDER BY 1")
    }

    private fun groupByExpressions(scope: Scope, sql: MutableSQLQuery) {
        with(sql.query) {
            append(" GROUP BY 1")
            for ((index, expression) in pql.selectExpressions[scope]!!.withIndex()) {
                if (expression.filter { it is Function && it.functionType == FunctionType.Aggregation }.any())
                    continue
                append(", ")
                append(index + 2)
            }
        }
    }
    // endregion

    // region select grouped ids
    private fun <T> groupedIdsQuery(scope: Scope, outerScopeGroup: T, logId: Int?): SQLQuery = SQLQuery {
        assert(
            (pql.isImplicitGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Log]!!) && scope > Scope.Log ||
                    (pql.isImplicitGroupBy[Scope.Trace]!! || pql.isGroupBy[Scope.Trace]!!) && scope > Scope.Trace
        )

        it.query.append("SELECT array_agg(id) AS ids FROM (")
        val (attributes, groupByAttributes) = selectInnerGroup(it, scope, logId, true)

        val desiredFromPosition = it.query.length

        whereGroup(scope, it, outerScopeGroup)

        val from = from(scope, it)
        it.query.insert(desiredFromPosition, from)

        groupById(scope, it)
        it.query.append(')')
        innerGroupSignature(it, attributes, groupByAttributes, true, scope)

        outerGroupBy(scope, it, attributes, groupByAttributes, logId, true)

        limit(scope, it)
        offset(scope, it)
    }

    private fun selectInnerGroup(
        sql: MutableSQLQuery,
        scope: Scope,
        logId: Int?,
        createOrderAttributeIfUngrouped: Boolean
    ): Pair<Set<Attribute>, List<Attribute>> = with(sql.query) {
        append("SELECT ${scope.alias}.id,")

        val groupByAttributes =
            (pql.groupByStandardAttributes[scope]!! +
                    pql.groupByOtherAttributes[scope]!!).flatMap { attr ->
                if (logId !== null && attr.isClassifier)
                    cache.expandClassifier(logId, attr)
                else
                    listOf(attr)
            }

        val orderByAttributes =
            pql.orderByExpressions[scope]!!.flatMap { order -> order.base.filter { it is Attribute } }
                .flatMap { attr ->
                    attr as Attribute
                    if (logId !== null && attr.isClassifier)
                        cache.expandClassifier(logId, attr)
                    else
                        listOf(attr)
                }

        val attributes = (groupByAttributes + orderByAttributes).toSet()
        for (attribute in attributes) {
            attribute.toSQL(
                sql, logId, Type.Any, 1,
                prefix = { sql ->
                    if (attribute.scope != scope) sql.query.append("array_agg(")
                },
                suffix = { sql ->
                    if (attribute.scope != scope) {
                        pql.orderByExpressions[attribute.scope]!!.toSQL(
                            sql,
                            logId,
                            "${attribute.scope!!.alias}.id",
                            true,
                            true
                        )
                        sql.query.append(")")
                    }
                },
                ignoreHoisting = true
            )
            append(',')
        }

        if (createOrderAttributeIfUngrouped && groupByAttributes.isEmpty()) {
            append("row_number() OVER (")
            if (scope != Scope.Log)
                append("PARTITION BY ${scope.alias}.${scope.upper}_id ")

            pql.orderByExpressions[scope]!!.toSQL(sql, logId, "${scope.alias}.id", true, true)
            append("),")
            sql.scopes.add(ScopeWithMetadata(scope, 0))
        }

        setLength(length - 1)
        return Pair(attributes, groupByAttributes)
    }

    private fun <T> whereGroup(scope: Scope, sql: MutableSQLQuery, outerScopeGroup: T) = with(sql.query) {
        sql.scopes.add(ScopeWithMetadata(scope, 0))

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (scope) {
            Scope.Event -> append(" WHERE e.trace_id=ANY(?)")
            Scope.Trace -> append(" WHERE t.log_id=ANY(?)")
            Scope.Log -> Unit
        }
        if (scope != Scope.Log)
            sql.params.add(outerScopeGroup!!)

        if (pql.whereExpression == Expression.empty)
            return@with

        val insertPos = length

        pql.whereExpression.toSQL(sql, Type.Any)
        if (length != insertPos) {
            insert(insertPos, if (scope == Scope.Log) " WHERE (" else " AND (")
            append(')')
        }
    }

    private fun innerGroupSignature(
        sql: MutableSQLQuery,
        attributes: Set<Attribute>,
        groupByAttributes: List<Attribute>,
        createOrderAttributeIfUngrouped: Boolean,
        scope: Scope
    ) = with(sql.query) {
        append(" ${scope.shortName} (id")
        if (attributes.isNotEmpty())
            append(',')
        append(attributes.indices.joinToString(",") { "a$it" })
        if (createOrderAttributeIfUngrouped && groupByAttributes.isEmpty())
            append(",ord")
        append(")")
    }

    private fun outerGroupBy(
        scope: Scope,
        sql: MutableSQLQuery,
        attributes: Set<Attribute>,
        groupByAttributes: List<Attribute>,
        logId: Int?,
        createOrderAttributeIfUngrouped: Boolean
    ) = with(sql.query) {
        if (!pql.isImplicitGroupBy[scope]!!) {
            append(" GROUP BY ")
            if (createOrderAttributeIfUngrouped && groupByAttributes.isEmpty()) {
                append("ord")
                append(" ORDER BY ord")
            } else {
                val attributeInverseMap = HashMap<Attribute, Int>().apply {
                    for ((index, attribute) in attributes.withIndex())
                        put(attribute, index)
                }

                for (attribute in groupByAttributes) {
                    val index = attributeInverseMap[attribute]
                    append("a$index,")
                }
                setLength(sql.query.length - 1)

                pql.orderByExpressions[scope]!!.toSQL(sql, logId, "1", true, true, attributeInverseMap)
            }
        }
    }
    // endregion

    // region Public interface
    fun getLogs(): Executor<LogQueryResult> = LogExecutor()
    fun getTraces(logId: Int): Executor<QueryResult> = TraceExecutor(logId)
    fun getEvents(logId: Int, traceId: Long): Executor<QueryResult> = EventExecutor(logId, traceId)

    abstract inner class Executor<T : QueryResult> {
        protected abstract val iterator: IdBasedIterator
        protected var connection: Connection? = null

        fun use(lambda: (iterator: IdBasedIterator) -> Unit) {
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
        fun skipBatch() = iterator.skip()

        abstract inner class IdBasedIterator : Iterator<T> {
            protected var offset: Long = 0L
            protected abstract val ids: Iterator<Any>
            override fun hasNext(): Boolean = ids.hasNext()
            fun skip() {
                var index = 0
                while (index++ < batchSize && ids.hasNext())
                    ids.next()
                offset += batchSize // offset may not reflect the actual position only if hasNext()==false
            }

            fun nextIds(): List<Any> = ids.take(batchSize)
        }
    }

    private inner class LogExecutor : Executor<LogQueryResult>() {
        override val iterator: IdBasedIterator = object : IdBasedIterator() {
            override val ids: Iterator<Any> = cache.topEntry.ids!!.iterator()

            override fun next(): LogQueryResult {
                val ids = nextIds()
                logger.trace { "Retrieving log/group ids: ${ids.joinToString()}." }
                val idParam = connection!!.createArrayOf("int", ids.toTypedArray())
                val entityParameters = cache.topEntry.queryEntity.params.fillPlaceholders(idParam, offset)
                val attrParameters = cache.topEntry.queryAttributes.params.fillPlaceholders(idParam, offset)
                val exprParameters = cache.topEntry.queryExpressions.params.fillPlaceholders(idParam, offset)
                val globalsParameters = cache.topEntry.queryGlobals.params.fillPlaceholders(idParam, offset)
                val idParamList = listOf(idParam)
                offset += batchSize

                val results = listOf(
                    cache.topEntry.queryEntity,
                    cache.topEntry.queryAttributes,
                    cache.topEntry.queryExpressions,
                    queryLogClassifiers,
                    queryLogExtensions,
                    cache.topEntry.queryGlobals
                ).executeMany(
                    connection!!,
                    entityParameters,
                    attrParameters,
                    exprParameters,
                    idParamList,
                    idParamList,
                    globalsParameters
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
            override val ids: Iterator<Any> = log.ids!!.iterator()

            override fun next(): QueryResult {
                val ids = nextIds()
                logger.trace { "Retrieving trace/group ids: ${ids.joinToString()}." }
                val idParam = connection!!.createArrayOf("bigint", ids.toTypedArray())
                val entityParameters = log.queryEntity.params.fillPlaceholders(idParam, offset)
                val attrParameters = log.queryAttributes.params.fillPlaceholders(idParam, offset)
                val exprParameters = log.queryExpressions.params.fillPlaceholders(idParam, offset)
                offset += batchSize

                val results = listOf(
                    log.queryEntity,
                    log.queryAttributes,
                    log.queryExpressions
                ).executeMany(connection!!, entityParameters, attrParameters, exprParameters)

                return QueryResult(ErrorSuppressingResultSet(results[0]), results[1], results[2])
            }
        }
    }

    private inner class EventExecutor(logId: Int, traceId: Long) : Executor<QueryResult>() {
        private val trace = cache.topEntry.logs[logId]!!.traces!![traceId]!!
        override val iterator: IdBasedIterator = object : IdBasedIterator() {
            override val ids: Iterator<Any> = trace.ids.iterator()

            override fun next(): QueryResult {
                val ids = nextIds()
                logger.trace { "Retrieving event/group ids: ${ids.joinToString()}." }
                val idParam = connection!!.createArrayOf("bigint", ids.toTypedArray())
                val entityParameters = trace.queryEntity.params.fillPlaceholders(idParam, offset)
                val attrParameters = trace.queryAttributes.params.fillPlaceholders(idParam, offset)
                val exprParameters = trace.queryExpressions.params.fillPlaceholders(idParam, offset)
                offset += batchSize

                val results = listOf(
                    trace.queryEntity,
                    trace.queryAttributes,
                    trace.queryExpressions
                ).executeMany(connection!!, entityParameters, attrParameters, exprParameters)

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
                        val clsName = if (classifier.isStandard) classifier.standardName else classifier.name
                        setInt(1, logId)
                        setString(2, classifier.scope.toString())
                        setString(3, clsName.substringAfter(":"))
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

        val entityQueryEvent by lazy(NONE) { entityQuery(Scope.Event) }
        val entityQueryTrace by lazy(NONE) { entityQuery(Scope.Trace) }
        val expressionsQueryEvent by lazy(NONE) { expressionsQuery(Scope.Event) }
        val expressionsQueryTrace by lazy(NONE) { expressionsQuery(Scope.Trace) }
        val groupingEntityQueryEvent by lazy(NONE) { groupEntityQuery(Scope.Event) }
        val groupingEntityQueryTrace by lazy(NONE) { groupEntityQuery(Scope.Trace) }
        val groupingExpressionsQueryEvent by lazy(NONE) { groupExpressionsQuery(Scope.Event) }
        val groupingExpressionsQueryTrace by lazy(NONE) { groupExpressionsQuery(Scope.Trace) }

        val topEntry: TopEntry by lazy(NONE) {
            when {
                !pql.isImplicitGroupBy[Scope.Log]!! && !pql.isGroupBy[Scope.Log]!! -> RegularTopEntry()
                pql.isImplicitGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Log]!! -> GroupingTopEntry()
                else -> throw IllegalArgumentException()
            }
        }

        abstract inner class Entry {
            abstract val ids: Iterable<Any>?
            abstract val queryIds: SQLQuery
            abstract val queryEntity: SQLQuery
            abstract val queryAttributes: SQLQuery
            abstract val queryExpressions: SQLQuery
        }

        abstract inner class TopEntry : Entry() {
            /**
             * key: logId or groupId
             * The order is important
             */
            abstract val logs: LinkedHashMap<Int, LogEntry>

            abstract val queryGlobals: SQLQuery
        }

        inner class RegularTopEntry : TopEntry() {
            init {
                assert(!pql.isImplicitGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Log]!!)
            }

            override val ids: Iterable<Int>
                get() = logs.keys

            override val logs: LinkedHashMap<Int, LogEntry> by lazy(NONE) {
                LinkedHashMap<Int, LogEntry>().apply {
                    connection.use {
                        val nextCtor =
                            if (pql.isImplicitGroupBy[Scope.Trace]!! || pql.isGroupBy[Scope.Trace]!!) ::GroupingLogEntry else ::RegularLogEntry

                        for (logId in queryIds.execute(it).toIdList { it.getInt(1) })
                            this[logId] = nextCtor(this@RegularTopEntry, logId)
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

        inner class GroupingTopEntry : TopEntry() {
            init {
                assert(pql.isImplicitGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Log]!!)
            }

            override val ids: Collection<IntArray> by lazy(NONE) {
                lateinit var list: Collection<IntArray>
                connection.use {
                    list = queryIds.execute(it).to2DIntArray()
                }
                list
            }

            /**
             * key: groupId
             * value: grouping LogEntry
             * The order is important.
             */
            override val logs: LinkedHashMap<Int, LogEntry> by lazy(NONE) {
                LinkedHashMap<Int, LogEntry>().apply {
                    for ((index, group) in ids.withIndex())
                        this[index + 1] = GroupedLogEntry(this@GroupingTopEntry, group)
                }
            }

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Log, null, null) }
            override val queryEntity: SQLQuery by lazy(NONE) { groupEntityQuery(Scope.Log) }
            override val queryGlobals: SQLQuery =
                SQLQuery { it.query.append("SELECT NULL AS id WHERE 0=1") } // FIXME: return empty ResultSet for this query
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Log, null) }
            override val queryExpressions: SQLQuery by lazy(NONE) { groupExpressionsQuery(Scope.Log) }
        }

        abstract inner class LogEntry(val parent: TopEntry) : Entry() {
            /**
             * key: traceId or groupId
             * The order is important.
             */
            abstract val traces: LinkedHashMap<*, TraceEntry>?
        }

        inner class RegularLogEntry(parent: TopEntry, val logId: Int) : LogEntry(parent) {
            init {
                assert(!pql.isImplicitGroupBy[Scope.Log]!!)
                assert(!pql.isImplicitGroupBy[Scope.Trace]!!)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
            }

            override val ids: Iterable<Long>
                get() = traces!!.keys

            override var traces: LinkedHashMap<Long, TraceEntry>? = null
                get() {
                    if (field === null) {
                        assert(parent.logs[logId] === this)
                        val nextCtor =
                            if (pql.isImplicitGroupBy[Scope.Event]!! || pql.isGroupBy[Scope.Event]!!) ::GroupingTraceEntry else ::RegularTraceEntry

                        // Initialize all sibling RegularLogEntries together
                        val queries = parent.logs.values.map { it.queryIds }
                        connection.use {
                            val results = queries.executeMany(it)
                            for ((index, log) in parent.logs.values.withIndex()) {
                                (log as RegularLogEntry).traces = LinkedHashMap<Long, TraceEntry>().apply {
                                    for (traceId in results[index].toIdList { it.getLong(1) })
                                        this[traceId] = nextCtor(log, logId, traceId)
                                }
                            }
                        }
                        assert(field !== null)
                    }
                    return field
                }

            override val queryIds: SQLQuery by lazy(NONE) { idQuery(Scope.Trace, logId, null) }
            override val queryEntity: SQLQuery get() = cache.entityQueryTrace
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Trace, logId) }
            override val queryExpressions: SQLQuery get() = cache.expressionsQueryTrace
        }

        open inner class GroupingLogEntry(parent: TopEntry, logId: Int) : LogEntry(parent) {
            protected open fun assert() {
                assert(!pql.isImplicitGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(pql.isImplicitGroupBy[Scope.Trace]!! || pql.isGroupBy[Scope.Trace]!!)
            }

            init {
                @Suppress("LeakingThis")
                assert()
            }

            override var ids: List<LongArray>? = null
                get() {
                    if (field === null) {
                        // Initialize all sibling GroupingLogEntries together
                        val queries = parent.logs.values.map { it.queryIds }

                        connection.use {
                            val results = queries.executeMany(it)
                            for ((index, log) in parent.logs.values.withIndex()) {
                                (log as GroupingLogEntry).ids = results[index].to2DLongArray()
                            }
                        }
                        assert(field !== null)
                    }
                    return field
                }

            /**
             * key: groupId
             * value: grouping trace
             * The order is important.
             */
            override val traces: LinkedHashMap<Long, TraceEntry>? by lazy(NONE) {
                LinkedHashMap<Long, TraceEntry>().apply {
                    for ((index, group) in ids!!.withIndex())
                        this[index.toLong() + 1L] = GroupedTraceEntry(this@GroupingLogEntry, logId, group)
                }
            }

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Trace, logId, null) }
            override val queryEntity: SQLQuery get() = cache.groupingEntityQueryTrace
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Trace, logId) }
            override val queryExpressions: SQLQuery get() = cache.groupingExpressionsQueryTrace
        }

        inner class GroupedLogEntry(parent: TopEntry, logGroup: IntArray) : GroupingLogEntry(parent, -1) {
            override fun assert() {
                assert(pql.isImplicitGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Log]!!)
            }

            override val queryIds: SQLQuery by lazy(NONE) { groupedIdsQuery(Scope.Trace, logGroup, null) }
        }

        abstract inner class TraceEntry(val parent: LogEntry) : Entry() {
            override val ids: Iterable<Any>
                get() = events!!

            /**
             * index: eventId or groupId
             * The order is important.
             */
            abstract var events: List<Any>?
        }

        inner class RegularTraceEntry(parent: LogEntry, val logId: Int, val traceId: Long) : TraceEntry(parent) {
            init {
                assert(!pql.isImplicitGroupBy[Scope.Log]!!)
                assert(!pql.isImplicitGroupBy[Scope.Trace]!!)
                assert(!pql.isImplicitGroupBy[Scope.Event]!!)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
                assert(!pql.isGroupBy[Scope.Event]!!)
            }

            override var events: List<Any>? = null
                get() {
                    if (field === null) {
                        // Initialize all sibling RegularTraceEntries together
                        val id2trace = parent.traces!!.values.associateBy { (it as RegularTraceEntry).traceId }

                        val query = SQLQuery { sql ->
                            with(sql.query) {
                                append("SELECT id FROM events e WHERE e.trace_id=x.trace_id")
                                pql.orderByExpressions[Scope.Event]!!.toSQL(sql, logId, "1", true, true)
                                pql.offset[Scope.Event]?.toInt()?.let { append(" OFFSET $it") }
                                pql.limit[Scope.Event]?.toInt()?.let { append(" LIMIT $it") }
                                append(") FROM events x WHERE trace_id = ANY(?)")
                                insert(0, "SELECT DISTINCT trace_id, ARRAY(")
                            }
                            sql.params.add(id2trace.keys.toTypedArray())
                        }
                        connection.use {
                            query.execute(it).use { rs ->
                                while (rs.next()) {
                                    val traceId = rs.getLong(1)
                                    val events = (rs.getArray(2).array as Array<Long>).toList()
                                    id2trace[traceId]!!.events = events
                                }
                            }
                        }
                        assert(field !== null)
                    }
                    return field
                }

            override val queryIds: SQLQuery by lazy(NONE) { throw UnsupportedOperationException("RegularTraceEntry does not expose queryIds") }
            override val queryEntity: SQLQuery get() = cache.entityQueryEvent
            override val queryAttributes: SQLQuery by lazy(NONE) { attributesQuery(Scope.Event, logId) }
            override val queryExpressions: SQLQuery get() = cache.expressionsQueryEvent
        }

        open inner class GroupingTraceEntry(parent: LogEntry, logId: Int?, traceId: Long) : TraceEntry(parent) {
            protected open fun assert() {
                assert(!pql.isImplicitGroupBy[Scope.Log]!!)
                assert(!pql.isImplicitGroupBy[Scope.Trace]!!)
                assert(!pql.isGroupBy[Scope.Log]!!)
                assert(!pql.isGroupBy[Scope.Trace]!!)
                assert(pql.isImplicitGroupBy[Scope.Event]!! || pql.isGroupBy[Scope.Event]!!)
            }

            init {
                @Suppress("LeakingThis")
                assert()
            }

            /**
             * index: groupId
             * value: group of eventIds
             * The order is important.
             * This is a one-based indexed list.
             */
            override var events: List<Any>? = null
                get() {
                    if (field === null) {
                        // Initialize all sibling GroupingTraceEntries at once
                        val queries = parent.traces!!.values.map { it.queryIds }
                        connection.use {
                            val results = queries.executeMany(it)
                            for ((index, trace) in parent.traces!!.values.withIndex()) {
                                val idList = results[index].to2DLongArray()
                                trace.events = object : List<LongArray> by idList {
                                    // one-based indexing
                                    override fun get(index: Int): LongArray = idList.get(index - 1)
                                }
                            }
                        }
                        assert(field !== null)
                    }
                    return field
                }

            override val queryIds: SQLQuery by lazy(NONE) { groupIdsQuery(Scope.Event, logId, traceId) }
            override val queryEntity: SQLQuery get() = cache.groupingEntityQueryEvent
            override val queryAttributes: SQLQuery by lazy(NONE) { groupAttributesQuery(Scope.Event, logId) }
            override val queryExpressions: SQLQuery get() = cache.groupingExpressionsQueryEvent
        }

        inner class GroupedTraceEntry(parent: LogEntry, logId: Int?, traceGroup: LongArray) :
            GroupingTraceEntry(parent, logId, -1L) {

            override fun assert() {
                assert(pql.isImplicitGroupBy[Scope.Log]!! || pql.isImplicitGroupBy[Scope.Trace]!! || pql.isImplicitGroupBy[Scope.Event]!! || pql.isGroupBy[Scope.Log]!! || pql.isGroupBy[Scope.Trace]!!)
            }

            override val queryIds: SQLQuery by lazy(NONE) { groupedIdsQuery(Scope.Event, traceGroup, logId) }
        }
    }

    private val Attribute.scopeWithMetadata
        get() = ScopeWithMetadata(this.scope!!, this.hoistingPrefix.length)

    private fun Attribute.toSQL(
        sql: MutableSQLQuery,
        logId: Int?,
        expectedType: Type,
        limitCount: Int,
        prefix: ((sql: MutableSQLQuery) -> Unit)? = null,
        suffix: ((sql: MutableSQLQuery) -> Unit)? = null,
        ignoreHoisting: Boolean = false,
        attributeToIndex: Map<Attribute, Int>? = null
    ): Int {
        val sqlScope = ScopeWithMetadata(this.scope!!, if (ignoreHoisting) 0 else this.hoistingPrefix.length)
        sql.scopes.add(sqlScope)
        // FIXME: move classifier expansion to database
        // expand classifiers
        val attributes = if (this.isClassifier) cache.expandClassifier(logId!!, this) else listOf(this)
        require(attributes.size <= limitCount) {
            "Line $line position $charPositionInLine: Classifier expansion yields ${attributes.size} but $limitCount allowed."
        }

        with(sql.query) {
            for (attribute in attributes) {
                assert(this@toSQL.scope == attribute.scope)

                if (prefix !== null) prefix(sql)

                val index = attributeToIndex?.let { it[attribute] }

                if (index !== null) {
                    append("a$index")
                } else if (attribute.isStandard) {
                    if (attribute.scope == Scope.Log && attribute.standardName == DB_ID)
                        append("l.id") // for backward-compatibility with the previous implementation of the XES layer
                    else
                        append("${sqlScope.alias}.\"${attribute.standardName}\"")
                } else {
                    // use function for the non-standard attribute
                    append("get_${attribute.scope}_attribute(${sqlScope.alias}.id, ?, ?::attribute_type, null::${expectedType.asDBType})")
                    sql.params.add(attribute.name)
                    sql.params.add(expectedType.asAttributeType)
                }

                if (suffix !== null) suffix(sql)

                append(',')
            }
            setLength(length - 1)
        }

        return attributes.size
    }

    private fun IExpression.toSQL(
        sql: MutableSQLQuery,
        _expectedType: Type,
        useEffectiveScope: Boolean = false,
        attributeToIndex: Map<Attribute, Int>? = null
    ) {
        with(sql.query) {
            fun walk(expression: IExpression, expectedType: Type) {
                when (expression) {
                    is Attribute -> expression.toSQL(
                        sql, null, if (expression.type != Type.Unknown) expression.type else expectedType, 1,
                        ignoreHoisting = false, attributeToIndex = attributeToIndex
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
                                val isTemporalSubtract =
                                    expression.value == "-" && expression.children.any { it.type == Type.Datetime }
                                if (isTemporalSubtract)
                                    append("extract(epoch from ")
                                for (i in expression.children.indices) {
                                    walk(expression.children[i], expression.expectedChildrenTypes[i])
                                    if (i < expression.children.size - 1) {
                                        when (expression.value) {
                                            "matches" -> append(" ~ ")
                                            "+" -> append(if (expression.children.any { it.type == Type.String }) " || " else " + ")
                                            else -> append(" ${expression.value} ")
                                        }
                                    }
                                }
                                if (isTemporalSubtract)
                                    append(")/$secondsPerDay")
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
                            "min", "max", "avg", "count", "sum" -> {
                                assert(expression.children.size == 1)
                                append("(SELECT ")
                                append(expression.name)
                                append("(a0) FROM (SELECT ")
                                if (!useEffectiveScope)
                                    append("DISTINCT ON (id) ")
                                append("a0 FROM unnest(array_agg(")
                                val attr = expression.filter { it is Attribute }.first() as Attribute
                                if (useEffectiveScope)
                                    append(attr.effectiveScope.alias)
                                else
                                    append(attr.scopeWithMetadata.alias)
                                append(".id), array_agg(")
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
                                append(")) s(id, a0)) s)")
                            }
                            "round", "lower", "upper" -> {
                                append(expression.name)
                                append('(')
                                walk(expression.children[0], expression.expectedChildrenTypes[0])
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

    private fun Iterable<OrderedExpression>.toSQL(
        sql: MutableSQLQuery,
        logId: Int?,
        expressionIfEmpty: String,
        printOrderBy: Boolean,
        printDirection: Boolean,
        attributeToIndex: Map<Attribute, Int>? = null
    ): Int {
        with(sql.query) {
            if (printOrderBy)
                append(" ORDER BY ")

            var orderByCount = 0
            for (expression in this@toSQL) {
                if (expression.base is Attribute) {
                    val suffix: (sql: MutableSQLQuery) -> Unit = {
                        if (printDirection && expression.direction == OrderDirection.Descending)
                            it.query.append(" DESC")
                    }
                    orderByCount += expression.base.toSQL(
                        sql, logId, Type.Any, Int.MAX_VALUE, null, suffix, false, attributeToIndex
                    )
                } else {
                    expression.base.toSQL(sql, Type.Any, true, attributeToIndex)
                    ++orderByCount
                    if (printDirection && expression.direction == OrderDirection.Descending)
                        append(" DESC")
                }
                append(", ")
            }
            if (orderByCount > 0)
                setLength(length - 2)
            else {
                orderByCount = 1
                append(expressionIfEmpty)
            }

            return@toSQL orderByCount
        }
    }
}
