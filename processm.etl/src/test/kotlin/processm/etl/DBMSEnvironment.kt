package processm.etl

import org.testcontainers.containers.JdbcDatabaseContainer
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import java.io.File
import java.sql.Connection
import java.util.*

/**
 * A common interface for external databases uses by tests in [processm.etl.jdbc]
 */
interface DBMSEnvironment<Container : JdbcDatabaseContainer<*>> : AutoCloseable {
    companion object {
        val TEST_DATABASES_PATH: File = File("../test-databases/")
    }

    val user: String
    val password: String
    val jdbcUrl: String

    /**
     * Properties in a format suitable for [processm.etl.helpers.getConnection]
     */
    val connectionProperties: ConnectionProperties
    fun connect(): Connection

    val dataConnector: DataConnector
        get() = DataConnector.new {
            name = UUID.randomUUID().toString()
            val sep = if ("?" in jdbcUrl) "&" else "?"
            connectionProperties =
                ConnectionProperties(ConnectionType.JdbcString, "$jdbcUrl${sep}user=$user&password=$password")
        }
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
    protected val container: Container
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
