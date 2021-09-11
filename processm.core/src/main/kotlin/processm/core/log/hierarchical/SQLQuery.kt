package processm.core.log.hierarchical

import org.slf4j.Logger
import processm.core.logging.logger
import processm.core.logging.trace
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

internal fun Collection<SQLQuery>.executeMany(connection: Connection, vararg params: List<Any>): List<ResultSet> {
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

    val result = ArrayList<ResultSet>(this.size)
    do {
        result.add(statement.resultSet)
    } while (statement.getMoreResults(Statement.KEEP_CURRENT_RESULT))

    assert(result.size == this.size)

    SQLQuery.logger.trace {
        val elapsed = System.currentTimeMillis() - ts
        if (elapsed >= 500) "Long-running query executed in ${elapsed}ms: $sql $params"
        else null
    }

    return result
}
