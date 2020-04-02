package processm.core.log.hierarchical

import java.sql.Connection
import java.sql.ResultSet

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
