package processm.etl

import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.lifecycle.Startables
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
    private val container: Container = initAndRun()

    protected abstract fun initContainer(): Container

    protected open fun initAndRun(): Container {
        val container = initContainer()
            .withDatabaseName(dbName)
            .withUsername(user)
            .withPassword(password)
            .withInitScript(schemaScript)
        Startables.deepStart(listOf(container)).join()

        container.createConnection("").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { s ->
                s.execute(this::class.java.classLoader.getResource(insertScript)!!.readText())
            }
            connection.commit()
        }

        return container as Container
    }

    val jdbcUrl: String = container.jdbcUrl

    fun connect(): Connection = container.createConnection("")

    override fun close() {
        container.close()
    }
}
