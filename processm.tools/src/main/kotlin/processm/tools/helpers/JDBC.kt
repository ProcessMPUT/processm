package processm.tools.helpers

import java.sql.Connection
import java.sql.SQLException
import java.sql.Types
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import kotlin.random.Random

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
    val logger = logger()
    val nTries = 10
    for (tryId in 1..nTries) {
        try {
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
        } catch (e: SQLException) {
            rollback()
            logger.warn("SQL exception on try $tryId/$nTries", e)
            if (tryId < nTries)
                Thread.sleep(100 + Random.nextLong(8) * 50) // 100..500 ms with 50 ms resolution
            else
                throw e
        }
    }
    throw IllegalStateException("This exception should never happen")
}

/**
 * A JDBC connection pool for use with coroutines.
 */
interface ConnectionPool {
    /**
     * Acquires a connection, executes [block] exactly once and releases the connection back to the pool
     */
    operator fun <R> invoke(block: (Connection) -> R): R

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect a single input parameter of the type P1 and return a ResultSet such that
     * its first column is of the type [R].
     *
     * The created function returns the first column of the result set returned by the stored procedure.
     */
    fun <P1, R> wrapStoredProcedure1RS1(name: String): (P1) -> List<R> =
        { p1 ->
            this { connection ->
                connection.call(name, listOf(p1))?.map { it.first() as R } ?: emptyList()
            }
        }

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect two parameters:
     * 1. an input parameter of the type [P1]
     * 2. an output parameter of the type [R]
     *
     * The created function returns the value of the output parameter.
     */
    fun <P1, R> wrapStoredProcedure2(type: Int, name: String): (P1) -> R =
        { p1 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, out))
                out.value as R
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
    fun <P1, P2, R> wrapStoredProcedure3(type: Int, name: String): (P1, P2) -> R =
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
    fun <P1, P2, P3, R> wrapStoredProcedure4(type: Int, name: String): (P1, P2, P3) -> R =
        { p1, p2, p3 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, p3, out))
                out.value as R
            }
        }

    /**
     * Creates a function that wraps calling the stored procedure named [name] by first acquiring a connection to the DB
     * using [invoke]. The procedure should expect four parameters:
     * 1. an input parameter of the type [P1]
     * 2. an input parameter of the type [P2]
     * 3. an input parameter of the type [P3]
     * 4. an input parameter of the type [P4]
     * 5. an output parameter of the type [R]
     *
     * The created function returns the value of the output parameter.
     */
    fun <P1, P2, P3, P4, R> wrapStoredProcedure5(type: Int, name: String): (P1, P2, P3, P4) -> R =
        { p1, p2, p3, p4 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, p3, p4, out))
                out.value as R
            }
        }
}

/**
 * A lazy implementation of [ConnectionPool] which creates connections on-demand, up to [maxSize] of them.
 * Connections are created by calling [createConnection] and they are never closed.
 */
class LazyConnectionPool(private val maxSize: Int, val createConnection: () -> Connection) :
    ConnectionPool {
    private val availableConnections = ArrayBlockingQueue<Connection>(maxSize)
    private val notCreatedConnections = Semaphore(maxSize)

    override operator fun <R> invoke(block: (Connection) -> R): R {
        val connection = availableConnections.poll() ?: run {
            if (notCreatedConnections.tryAcquire())
                createConnection()
            else
                availableConnections.take()
        }
        val result = block(connection)
        availableConnections.add(connection)
        return result
    }
}