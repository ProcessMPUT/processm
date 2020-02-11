package processm.core.log

import processm.core.persistence.DBConnectionPool

class DatabaseXESOutputStream : XESOutputStream {
    private val connection = DBConnectionPool.getConnection()

    init {
        connection.autoCommit = false
    }

    override fun write(element: XESElement) {
        TODO("Write element to database. You may need to remember context: log_id and trace_id.")
    }

    override fun close() {
        connection.commit()
        connection.close()
    }
}