package processm.core.persistence.connection

import org.postgresql.ds.PGSimpleDataSource
import processm.core.Brand
import processm.core.helpers.getPropertyIgnoreCase
import processm.core.helpers.isUUID
import processm.core.helpers.loadConfiguration
import kotlin.properties.Delegates

object DatabaseChecker {
    const val jdbcPostgresqlStart = "jdbc:postgresql://"
    lateinit var baseConnectionURL:String
        private set
    var mainDatabaseName: String by Delegates.notNull()
        private set

    init {
        init()
    }

    private fun init() {
        baseConnectionURL = readDatabaseConnectionURL()
        ensurePostgreSQLDatabase()
        mainDatabaseName = ensureMainDBNameNotUUID()
    }

    /**
     * Invalidates the DB pool cache by calling [DBCache.invalidate] and re-reads the database configurations
     */
    fun reloadConfiguration() {
        DBCache.invalidate()
        init()
    }

    /**
     * Read persistence connection URL from system's property.
     */
    private fun readDatabaseConnectionURL(): String {
        loadConfiguration()
        return checkNotNull(getPropertyIgnoreCase("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL")) {
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
