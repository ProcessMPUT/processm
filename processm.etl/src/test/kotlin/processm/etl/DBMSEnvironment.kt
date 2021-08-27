package processm.etl

import org.testcontainers.containers.JdbcDatabaseContainer
import java.sql.Connection

/**
 * The base class for simulating external databases. The derived classes implement the specifics of concrete database
 * management systems.
 */
abstract class DBMSEnvironment<Container : JdbcDatabaseContainer<*>>(
    val dbName: String,
    val user: String,
    val password: String,
    val schemaScript: String,
    val insertScript: String
) : AutoCloseable {
    private val containerDelegate = lazy { initAndRun() }
    private val container: Container by containerDelegate

    protected abstract fun initContainer(): Container

    protected abstract fun initAndRun(): Container

    val jdbcUrl: String
        get() = container.jdbcUrl

    fun connect(): Connection = container.createConnection("")

    override fun close() {
        if (containerDelegate.isInitialized())
            container.close()
    }
}
