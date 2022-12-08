package processm.core.persistence.copy

import org.postgresql.PGConnection
import org.postgresql.copy.PGCopyOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.sql.Connection


class EagerCopy(
    connection: Connection,
    destination: String,
    prefix: Collection<String> = emptyList(),
    extraColumnValues: Collection<String> = emptyList()
) :
    Copy(destination, extraColumnValues), Closeable {

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
        PrintStream(PGCopyOutputStream(connection.unwrap(PGConnection::class.java), sql))
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
