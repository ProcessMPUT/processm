package processm.tools.helpers

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.sync.Semaphore
import java.sql.Connection
import java.sql.Types


class Out<T>(val type: Int) {
    var value: T? = null
        private set

    fun set(value: Any?) {
        this.value = value as T
    }
}

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


interface CoroutinesConnectionPool {
    suspend operator fun <R> invoke(block: suspend (Connection) -> R): R

    fun <P1, R> wrapStoredProcedure1RS1(name: String): suspend (P1) -> List<R> =
        { p1 ->
            this { connection ->
                connection.call(name, listOf(p1))?.map { it.first() as R } ?: emptyList()
            }
        }

    fun <P1, P2, R> wrapStoredProcedure3(type: Int, name: String): suspend (P1, P2) -> R =
        { p1, p2 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, out))
                out.value as R
            }
        }

    fun <P1, P2, P3, R> wrapStoredProcedure4(type: Int, name: String): suspend (P1, P2, P3) -> R =
        { p1, p2, p3 ->
            this { connection ->
                val out = Out<R>(type)
                connection.call(name, listOf(p1, p2, p3, out))
                out.value as R
            }
        }
}

class LazyCoroutinesConnectionPool(maxSize: Int, val createConnection: () -> Connection) : CoroutinesConnectionPool {
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