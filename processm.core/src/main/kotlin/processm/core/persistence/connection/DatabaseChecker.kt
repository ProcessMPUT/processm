package processm.core.persistence.connection

import org.postgresql.ds.PGSimpleDataSource
import processm.core.helpers.isUUID

object DatabaseChecker {
    /**
     * Read persistence connection URL from system's property.
     */
    fun readDatabaseConnectionURL(): String {
        // FIXME: remove before merge
        System.setProperty(
            "PROCESSM.CORE.PERSISTENCE.CONNECTION.URL",
            "jdbc:postgresql://db.processm.cs.put.poznan.pl/db?user=bgorka&password=bgorka"
        )

        return System.getProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL")
    }

    /**
     * Validate connection as `jdbc:posgresql://` connection which will be used in regex.
     */
    fun ensurePostgreSQLDatabase(connectionURL: String) {
        require(connectionURL.startsWith("jdbc:postgresql://")) { "Expected PostgreSQL database not found!" }
    }

    /**
     * Validate main database name - should not be in UUID format.
     * As result return database name.
     */
    fun ensureMainDBNameNotUUID(connectionURL: String): String {
        val databaseName = with(PGSimpleDataSource()) {
            setURL(connectionURL)
            return@with databaseName!!
        }

        require(!databaseName.isUUID()) { "Database name can't be in UUID format" }

        return databaseName
    }

    /**
     * Switch database by create new connection URL to selected database.
     */
    fun switchDatabaseURL(baseConnectionURL: String, mainDatabaseName: String, expectedDatabase: String): String {
        val withoutPSQL = baseConnectionURL.substring(18)
        return "jdbc:postgresql://${Regex("/${mainDatabaseName}").replace(withoutPSQL, "/$expectedDatabase")}"
    }
}