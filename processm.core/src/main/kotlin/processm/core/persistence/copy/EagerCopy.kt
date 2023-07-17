package processm.core.persistence.copy

import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.nio.charset.Charset
import java.sql.Connection


/**
 * A wrapper around Postgres-specific COPY FROM operation with eager execution, i.e., the COPY command is started immediately
 * upon creation of the instance and the data is passed to the DB as they arrive.
 *
 * Values are added by calling to [add] functions, and the current row is ended by calling to [flushRow]. To flush the
 * data and end the COPY command, one must call to [close].
 *
 * @param destination The part of the COPY query to insert between COPY and FROM, as per PostgreSQL's documentation.
 * @param prefix A collection of constant values to prepend each inserted row
 * @param extraColumnValues A collection of constant values to append to each inserted row
 */
class EagerCopy(
    connection: Connection,
    destination: String,
    prefix: Collection<String> = emptyList(),
    extraColumnValues: Collection<String> = emptyList()
) : Copy(destination, extraColumnValues), Closeable {

    private val prefix: String = if (prefix.isNotEmpty()) {
        with(StringBuilder()) {
            prefix.forEach {
                append(escape(it) ?: NULL)
                append(DELIMITER)
            }
            toString()
        }
    } else ""

    private val stream: PrintStream =
        PrintStream(
            PGCopyOutputStream(connection.unwrap(PGConnection::class.java), sql),
            false,
            Charset.forName("utf-8")
        )
    private var newRow: Boolean = true

    override fun addInternal(text: String?) {
        stream.print(if (newRow) prefix else DELIMITER)
        if (text !== null)
            stream.print(text)
        else
            stream.print(NULL)
        newRow = false
    }

    fun flushRow() {
        stream.println(suffix)
        newRow = true
    }

    override fun close() =
        stream.close()
}
