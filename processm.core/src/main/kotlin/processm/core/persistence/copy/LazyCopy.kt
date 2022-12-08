package processm.core.persistence.copy

import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import java.io.PrintStream
import java.sql.Connection


/**
 * A wrapper around Postgres's COPY FROM STDIN handling escaping and mapping indexes to real IDs
 */
class LazyCopy(destination: String, extraColumnValues: Collection<String> = emptyList()) :
    Copy(destination, extraColumnValues) {

    private val data = ArrayList<Pair<Int, String>>()

    private var current = StringBuilder()
    override fun addInternal(text: String?): Unit = with(current) {
        append(DELIMITER)
        if (text !== null)
            append(text)
        else
            append(NULL)
    }


    fun flushRow(rootIndex: Int) {
        data.add(rootIndex to current.toString())
        current.clear()
    }

    fun execute(connection: Connection, ids: List<Long>) {
        if (data.isEmpty())
            return
        val pgConnection = connection.unwrap(PGConnection::class.java)
        PGCopyOutputStream(pgConnection, sql).use {
            PrintStream(it).use { out ->
                for ((idx, row) in data) {
                    out.print(ids[idx])
                    out.print(row)
                    out.println(suffix)
                }
            }
        }
    }
}