package processm.core.persistence.connection

import org.postgresql.ds.PGSimpleDataSource
import processm.core.helpers.isUUID
import kotlin.properties.Delegates

object DatabaseChecker {
    private const val mainDBInternalName = "processm"

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
        println(System.getProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL"))
        return System.getProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL")
    }

    /**
     * Validate connection as `jdbc:posgresql://` connection which will be used in regex.
     */
    private fun ensurePostgreSQLDatabase() {
        require(baseConnectionURL.startsWith("jdbc:postgresql://")) { "Expected PostgreSQL database not found!" }
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
     * Switch database by create new connection URL to selected database.
     */
    fun switchDatabaseURL(expectedDatabase: String): String {
        // Main ProcessM database
        if (expectedDatabase == mainDBInternalName) return baseConnectionURL

        val withoutPSQL = baseConnectionURL.substring(18)
        return "jdbc:postgresql://${Regex("/${mainDatabaseName}").replace(withoutPSQL, "/$expectedDatabase")}"
    }
}