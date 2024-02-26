package processm.core.log.hierarchical

import org.postgresql.PGStatement
import org.slf4j.Logger
import processm.logging.logger
import processm.logging.trace
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

internal class SQLQuery(lambda: (sql: MutableSQLQuery) -> Unit) {
    companion object {
        internal val logger: Logger = logger()
    }

    val query: String
    val params: List<Any>

    init {
        val sql = MutableSQLQuery()
        lambda(sql)
        query = sql.query.toString()
        params = sql.params
    }

    fun execute(connection: Connection, params: List<Any> = this.params): ResultSet {
        val ts = System.currentTimeMillis()

        //logger.trace(query)

        val result = connection
            .prepareStatement(query)
            .apply {
                for ((i, p) in params.withIndex())
                    this.setObject(i + 1, p)
                closeOnCompletion()
            }.executeQuery()

        logger.trace {
            val elapsed = System.currentTimeMillis() - ts
            if (elapsed >= 500) "Long-running query executed in ${elapsed}ms: $query $params"
            else null
        }

        return result
    }
}

internal class MutableSQLQuery {
    val query: StringBuilder = StringBuilder()
    val params: MutableList<Any> = ArrayList()
    val scopes: MutableSet<ScopeWithMetadata> = HashSet()
}

internal fun List<SQLQuery>.executeMany(connection: Connection, vararg params: List<Any>): List<ResultSet> {
    fun handleBatch(first: Int, endExclusive: Int, result: MutableList<ResultSet>) {
        val queriesBatch = this.subList(first, endExclusive)
        val paramsBatch = if (params.size < first) emptyArray()
        else params.sliceArray(first until endExclusive.coerceAtMost(params.size))
        queriesBatch.executeManyNoSplitting(connection, result, paramsBatch)
    }

    val nParams =
        this.withIndex().map { (index, query) -> (if (index >= params.size) query.params else params[index]).size }
    val limit = 65535
    require(nParams.all { it <= limit }) { "Cannot handle a single query with at least $limit parameters due to the limitations of PostgreSQL" }
    if (nParams.sum() < limit) {
        val result = ArrayList<ResultSet>(this.size)
        this.executeManyNoSplitting(connection, result, params)
        return result
    }

    var sumSoFar = 0
    var start = 0
    val result = ArrayList<ResultSet>(this.size)
    for ((index, n) in nParams.withIndex()) {
        if (sumSoFar + n >= limit) {
            assert(start < index)
            handleBatch(start, index, result)
            start = index
            sumSoFar = n
        } else
            sumSoFar += n
    }
    assert(sumSoFar < limit)
    handleBatch(start, nParams.size, result)
    assert(this.size == result.size)
    return result
}

internal fun List<SQLQuery>.executeMultipleTimes(connection: Connection, process: (Int, ResultSet) -> Boolean) {
    assert(all { this[0].query == it.query })
    this[0].executeMultipleTimes(connection, this.map(SQLQuery::params), process)
}

internal fun SQLQuery.executeMultipleTimes(
    connection: Connection,
    params: List<List<Any>>,
    process: (Int, ResultSet) -> Boolean
) {
    val ts = System.currentTimeMillis()
    connection.prepareStatement(query, Statement.NO_GENERATED_KEYS).use { statement ->
        // Force using server-side prepared statement from the first query execution. Based on https://access.crunchydata.com/documentation/pgjdbc/42.2.8/server-prepare.html
        statement.unwrap(PGStatement::class.java).prepareThreshold = 1
        for (index in params.indices) {
            for ((i, p) in params[index].withIndex())
                statement.setObject(i + 1, p)
            if (!statement.executeQuery().use { process(index, it) })
                break
        }
        SQLQuery.logger.trace {
            val elapsed = System.currentTimeMillis() - ts
            if (elapsed >= 500) "Long-running query executed ${params.size} times in ${elapsed}ms: $query"
            else null
        }
    }
}

private fun List<SQLQuery>.executeManyNoSplitting(
    connection: Connection,
    result: MutableList<ResultSet>,
    params: Array<out List<Any>>
) {
    val ts = System.currentTimeMillis()

    val sql = StringBuilder()
    for (query in this) {
        sql.append(query.query)
        sql.append(';')
    }

    var paramIndex = 1
    val statement = connection.prepareStatement(sql.toString(), Statement.NO_GENERATED_KEYS)

    for ((index, query) in this.withIndex()) {
        assert(index >= params.size || params[index] !== null)
        val effectiveParams = if (index >= params.size) query.params else params[index]
        for (p in effectiveParams)
            statement.setObject(paramIndex++, p)
    }

    //SQLQuery.logger.trace { statement.toString() }

    statement.execute()

    do {
        result.add(statement.resultSet)
    } while (statement.getMoreResults(Statement.KEEP_CURRENT_RESULT))

    SQLQuery.logger.trace {
        val elapsed = System.currentTimeMillis() - ts
        if (elapsed >= 500) "Long-running query executed in ${elapsed}ms: $sql $params"
        else null
    }
}
