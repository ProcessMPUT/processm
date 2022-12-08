package processm.core.persistence.copy

import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import java.io.PrintStream
import java.sql.Connection


/**
 * A wrapper around Postgres's COPY FROM STDIN with lazy execution semantics. Data is added by calling to the [add] functions,
 * and each row is terminated by calling to [flushRow]. The rows are stored in memory until [execute] is called, when
 * the actual COPY command is issued and the data are written to the database.
 *
 * The first column of [destination] is expected to be a column with initially-unknown values. Thus, [flushRow] expects
 * a single [Int] argument, which is a proxy for this column. This proxy is then used to access the actual values for the
 * unknown column in [execute].
 *
 * @param destination The part of the COPY query to insert between COPY and FROM, as per PostgreSQL's documentation.
 * @param extraColumnValues A collection of constant values to append to each inserted row. Can be updated later by calling [setExtraColumnValues]
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

    fun flushRow(proxyId: Int) {
        data.add(proxyId to current.toString())
        current.clear()
    }

    /**
     * @param ids A list of values that map the proxyIds passed to [flushRow]: each `proxyId` is replaced by `ids[proxyId]`,
     * and the value is written as the first element of the corresponding row.
     */
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