package processm.core.persistence.connection

import org.postgresql.ds.PGSimpleDataSource
import processm.core.Brand
import processm.core.helpers.getPropertyIgnoreCase
import processm.core.helpers.isUUID
import processm.core.helpers.loadConfiguration
import processm.core.logging.loggedScope
import java.sql.DriverManager
import kotlin.properties.Delegates

object DatabaseChecker {
    const val databaseConnectionURLProperty = "PROCESSM.CORE.PERSISTENCE.CONNECTION.URL"
    const val reservedConnectionsPropertyName = "PROCESSM.CORE.PERSISTENCE.CONNECTION.RESERVED"
    const val jdbcPostgresqlStart = "jdbc:postgresql://"
    lateinit var baseConnectionURL: String
        private set
    var mainDatabaseName: String by Delegates.notNull()
        private set
    var maxPoolSize: Int by Delegates.notNull()
        private set

    init {
        init()
    }

    private fun init() {
        loggedScope { logger ->
            baseConnectionURL = readDatabaseConnectionURL()
            ensurePostgreSQLDatabase()
            mainDatabaseName = ensureMainDBNameNotUUID()
            val maxConnections = readMaxConnections() ?: error("Cannot read max_connections")
            logger.debug("Read max_connections: $maxConnections")
            check(maxConnections >= 2) { "The underlying database is configured incorrectly. Increase the max_connections parameter. Currently it is set to $maxConnections" }
            val reserved = getPropertyIgnoreCase(reservedConnectionsPropertyName)?.toIntOrNull() ?: 10
            maxPoolSize = maxConnections - reserved
            logger.info("Set max pool size to $maxPoolSize")
            check(maxPoolSize >= 1) {
                """Too many connections reserved for other clients. Modify the `max_connections`
                |parameter of the database (currently: $maxConnections) or the `PROCESSM.CORE.PERSISTENCE.CONNECTION.RESERVED`
                |configuration property of ProcessM (currently: $reserved). `max_connections` must exceed
                |`PROCESSM.CORE.PERSISTENCE.CONNECTION.RESERVED`.""".trimMargin()
            }
        }
    }

    /**
     * Invalidates the DB pool cache by calling [DBCache.invalidate] and re-reads the database configurations
     */
    fun reloadConfiguration() {
        DBCache.invalidate()
        init()
    }

    /**
     * Returns the value of Postgres `max_connections` or null if reading fails
     *
     * It directly uses [DriverManager] instead of [DBCache] to avoid circular dependency - the read value is required by [DBCache]
     */
    private fun readMaxConnections(): Int? = DriverManager.getConnection(baseConnectionURL).use { connection ->
        connection.prepareStatement("SHOW max_connections").use { statement ->
            statement.executeQuery().use { resultSet ->
                if (resultSet.next())
                    resultSet.getInt(1)
                else
                    null
            }
        }
    }

    /**
     * Read persistence connection URL from system's property.
     */
    private fun readDatabaseConnectionURL(): String {
        loadConfiguration()
        return checkNotNull(getPropertyIgnoreCase(databaseConnectionURLProperty)) {
            "Database connection string is not set. Set the property processm.core.persistence.connection.URL."
        }
    }

    /**
     * Validate connection as `jdbc:posgresql://` connection which will be used in regex.
     */
    private fun ensurePostgreSQLDatabase() {
        require(baseConnectionURL.startsWith(jdbcPostgresqlStart)) { "Expected PostgreSQL database not found!" }
    }

    /**
     * Validate main database name - should not be in UUID format.
     * As result return database name.
     */
    private fun ensureMainDBNameNotUUID(): String {
        val databaseName = with(PGSimpleDataSource()) {
            setURL(baseConnectionURL)
            return@with databaseName!!
        }

        require(!databaseName.isUUID()) { "Database name can't be in UUID format" }

        return databaseName
    }

    /**
     * Switches the database by creating new connection URL to the selected database.
     */
    fun switchDatabaseURL(expectedDatabase: String): String {
        // Main ProcessM database
        if (expectedDatabase == Brand.mainDBInternalName) return baseConnectionURL
        require(expectedDatabase.isUUID()) { "Datastore DB should be in UUID format" }
        val withoutPSQL = baseConnectionURL.substring(jdbcPostgresqlStart.length)

        return with(StringBuilder()) {
            append(jdbcPostgresqlStart)
            append(Regex("/${mainDatabaseName}").replace(withoutPSQL, "/$expectedDatabase"))
            toString()
        }
    }
}
