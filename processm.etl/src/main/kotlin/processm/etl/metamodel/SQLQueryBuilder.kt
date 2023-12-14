package processm.etl.metamodel

import processm.core.helpers.mapToArray
import java.sql.Connection
import java.sql.JDBCType
import java.sql.PreparedStatement
import java.util.*

/**
 * Syntactic sugar to simplify building SQL queries.
 * Exposes interface similar to a [StringBuilder] and an additional method [bind] to keep track of values to bind to variables.
 * In general limited in scope to what was needed, thus not necessarily suitable for making it public.
 *
 * To be used with [buildSQLQuery] and [Connection.prepareStatement]
 */
internal class SQLQueryBuilder {
    val queryBuilder = StringBuilder()
    val variables = ArrayList<Any>()

    val length: Int
        get() = queryBuilder.length

    fun <T> append(argument: T) {
        queryBuilder.append(argument)
    }

    fun deleteCharAt(index: Int) {
        queryBuilder.deleteCharAt(index)
    }

    fun delete(start: Int, end: Int) {
        queryBuilder.delete(start, end)
    }

    fun <T : Any> bind(argument: T) {
        variables.add(argument)
    }
}

internal inline fun buildSQLQuery(block: SQLQueryBuilder.() -> Unit): SQLQueryBuilder =
    SQLQueryBuilder().apply(block)

/**
 * Prepare statement and bind variables from the given [query].
 * Performs (very limited in scope) type recognition and conversion.
 */
internal fun Connection.prepareStatement(query: SQLQueryBuilder): PreparedStatement {
    val stmt = prepareStatement(query.queryBuilder.toString())
    for ((i, v) in query.variables.withIndex()) {
        when (v) {
            is String -> stmt.setString(i + 1, v)
            is Int -> stmt.setInt(i + 1, v)
            is RemoteObjectID -> stmt.setString(i + 1, v.toDB())
            is UUID -> stmt.setString(i + 1, v.toString())
            is Collection<*> -> {
                val first = requireNotNull(v.first())
                when (v.first()) {
                    is String -> stmt.setArray(i + 1, createArrayOf(JDBCType.VARCHAR.name, v.toTypedArray()))
                    is Int -> stmt.setArray(i + 1, createArrayOf(JDBCType.INTEGER.name, v.toTypedArray()))
                    is RemoteObjectID ->
                        stmt.setArray(
                            i + 1,
                            createArrayOf(JDBCType.VARCHAR.name, v.mapToArray { (it as RemoteObjectID).toDB() })
                        )

                    else -> TODO("Unsupported type in array: ${first::class}")
                }

            }

            else -> TODO("Unsupported type: ${v::class}")
        }
    }
    return stmt
}