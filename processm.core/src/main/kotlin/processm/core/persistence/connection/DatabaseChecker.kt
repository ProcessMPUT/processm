package processm.core.persistence.connection

import org.postgresql.ds.PGSimpleDataSource
import processm.core.Brand
import processm.core.helpers.isUUID
import processm.core.helpers.loadConfiguration
import kotlin.properties.Delegates

object DatabaseChecker {
    const val jdbcPostgresqlStart = "jdbc:postgresql://"
    var baseConnectionURL = readDatabaseConnectionURL()
        private set
    var mainDatabaseName: String by Delegates.notNull()
        private set

    init {
        ensurePostgreSQLDatabase()
        mainDatabaseName = ensureMainDBNameNotUUID()
    }

    /**
     * Read persistence connection URL from system's property.
     */
    private fun readDatabaseConnectionURL(): String {
        loadConfiguration()
        return System.getProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL")
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
        require(expectedDatabase.isUUID()) { "Datasource DB should be in UUID format" }
        val withoutPSQL = baseConnectionURL.substring(jdbcPostgresqlStart.length)

        return with(StringBuilder()) {
            append(jdbcPostgresqlStart)
            append(Regex("/${mainDatabaseName}").replace(withoutPSQL, "/$expectedDatabase"))
            toString()
        }
    }
}