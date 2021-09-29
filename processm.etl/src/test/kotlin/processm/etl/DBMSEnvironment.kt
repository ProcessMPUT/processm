package processm.etl

import org.testcontainers.containers.JdbcDatabaseContainer
import java.sql.Connection

/**
 * A common interface for external databases uses by tests in [processm.etl.jdbc]
 */
interface DBMSEnvironment<Container : JdbcDatabaseContainer<*>> : AutoCloseable {
    val user: String
    val password: String
    val jdbcUrl: String
    fun connect(): Connection
}

/**
 * The base class for simulating external databases. The derived classes implement the specifics of concrete database
 * management systems.
 */
abstract class AbstractDBMSEnvironment<Container : JdbcDatabaseContainer<*>>(
    val dbName: String,
    override val user: String,
    override val password: String
) : DBMSEnvironment<Container> {
    private val containerDelegate = lazy { initAndRun() }
    private val container: Container
        get() = containerDelegate.value

    protected abstract fun initContainer(): Container

    protected abstract fun initAndRun(): Container

    override val jdbcUrl: String
        get() = container.jdbcUrl

    override fun connect(): Connection = container.createConnection("")

    override fun close() {
        if (containerDelegate.isInitialized())
            container.close()
    }
}
