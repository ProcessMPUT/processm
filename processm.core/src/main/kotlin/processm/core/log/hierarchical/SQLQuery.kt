package processm.core.log.hierarchical

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

internal class SQLQuery(lambda: (sql: MutableSQLQuery) -> Unit) {
    val query: String
    val params: List<Any>

    init {
        val sql = MutableSQLQuery()
        lambda(sql)
        query = sql.query.toString()
        params = sql.params
    }

    fun execute(connection: Connection, params: List<Any> = this.params): ResultSet =
        connection
            .prepareStatement(query)
            .apply {
                for ((i, p) in params.withIndex())
                    this.setObject(i + 1, p)
            }.executeQuery()
}

internal class MutableSQLQuery {
    val query: StringBuilder = StringBuilder()
    val params: MutableList<Any> = ArrayList()
    val scopes: MutableSet<ScopeWithHoisting> = HashSet()
}

internal fun Collection<SQLQuery>.executeMany(connection: Connection, vararg params: List<Any>): List<ResultSet> {
    val sql = StringBuilder()

    for (query in this) {
        sql.append(query.query)
        sql.append(';')
    }

    var paramIndex = 1
    val statement = connection.prepareStatement(sql.toString(), Statement.NO_GENERATED_KEYS)

    for ((index, query) in this.withIndex()) {
        val effectiveParams = if (index >= params.size || params[index] === null) query.params else params[index]
        for (p in effectiveParams)
            statement.setObject(paramIndex++, p)
    }

    statement.execute()

    val result = ArrayList<ResultSet>(this.size)
    do {
        result.add(statement.resultSet)
    } while (statement.getMoreResults(Statement.KEEP_CURRENT_RESULT))

    assert(result.size == this.size)
    return result
}