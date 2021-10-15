package processm.tools.helpers

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.sync.Semaphore
import java.sql.Connection
import java.sql.Types

/**
 * A wrapper to be used with [Connection.call] to indicate the return parameter of a stored procedure
 *
 * @param type A value consistent with the second argument of [java.sql.CallableStatement.registerOutParameter]
 */
class Out<T>(val type: Int) {
    var value: T? = null
        private set

    fun set(value: Any?) {
        this.value = value as T
    }
}

/**
 * A generic wrapper to call a stored procedure. If the procedure has output parameters, an object of type [Out] should
 * be passed at the corresponding position and after the call to [call] returns, the returned value is available in [Out.value].
 * If the call returns a [java.sql.ResultSet] it is materialized and returned as a list of rows.
 *
 * @param name The name of the stored procedure
 * @param args The list of parameters, output parameters are to be of type [Out]

 * @return The [java.sql.ResultSet] returned by the call, materialized as a list of rows. The order in each row is the same as the
 * order of columns in the the result set. If the call does not return a result set, `null` is returned.
 */
fun Connection.call(name: String, args: List<Any?>): List<List<Any?>>? {
    val questionMarks = args.filter { it !is Out<*> || it.type != Types.NULL }.joinToString(separator = ", ") { "?" }
    val sql = "{call $name($questionMarks)}"
    val result = prepareCall(sql).use { stmt ->
        for ((i, arg) in args.withIndex())
            if (arg is Out<*>) {
                if (arg.type != Types.NULL)
                    stmt.registerOutParameter(i + 1, arg.type)
            } else
                stmt.setObject(i + 1, arg)
        val result = if (stmt.execute())
            stmt.resultSet.use { rs ->
                val result = ArrayList<List<Any?>>()
                while (rs.next()) {
                    result.add((1..rs.metaData.columnCount).map { rs.getObject(it) })
                }
                return@use result
            } else null
        for ((i, arg) in args.withIndex())
            if (arg is Out<*> && arg.type != Types.NULL)
                arg.set(stmt.getObject(i + 1))
        return@use result
    }
    commit()
    return result
}

/**
 * A JDBC connection pool for use with coroutines.
 */
interface CoroutinesConnectionPool {
    /**
     * Acquires a connection, executes [block] exactly once and releases the connection back to the pool
     */
    suspend operator fun <R> invoke(block: suspend (Connection) -> R): R

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect a single input parameter of the type P1 and return a ResultSet such that
     * its first column is of the type [R].
     *
     * The created function returns the first column of the result set returned by the stored procedure.
     */
    fun <P1, R> wrapStoredProcedure1RS1(name: String): suspend (P1) -> List<R> =
        { p1 ->
            this { connection ->
                connection.call(name, listOf(p1))?.map { it.first() as R } ?: emptyList()
            }
        }

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect three parameters:
     * 1. an input parameter of the type [P1]
     * 2. an input parameter of the type [P2]
     * 3. an output parameter of the type [R]
     *
     * The created function returns the value of the output parameter.
     */
    fun <P1, P2, R> wrapStoredProcedure3(type: Int, name: String): suspend (P1, P2) -> R =
        { p1, p2 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, out))
                out.value as R
            }
        }

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect four parameters:
     * 1. an input parameter of the type [P1]
     * 2. an input parameter of the type [P2]
     * 3. an input parameter of the type [P3]
     * 4. an output parameter of the type [R]
     *
     * The created function returns the value of the output parameter.
     */
    fun <P1, P2, P3, R> wrapStoredProcedure4(type: Int, name: String): suspend (P1, P2, P3) -> R =
        { p1, p2, p3 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, p3, out))
                out.value as R
            }
        }
}

/**
 * A lazy implementation of [CoroutinesConnectionPool] which creates connections on-demand, up to [maxSize] of them.
 * Connections are created by calling [createConnection] and they are never closed.
 */
class LazyCoroutinesConnectionPool(private val maxSize: Int, val createConnection: () -> Connection) :
    CoroutinesConnectionPool {
    private val availableConnections = Channel<Connection>(maxSize)
    private val notCreatedConnections = Semaphore(maxSize)

    override suspend operator fun <R> invoke(block: suspend (Connection) -> R): R {
        val connection = availableConnections.tryReceive().getOrElse {
            if (notCreatedConnections.tryAcquire())
                createConnection()
            else
                availableConnections.receive()
        }
        val result = block(connection)
        availableConnections.send(connection)
        return result
    }
}