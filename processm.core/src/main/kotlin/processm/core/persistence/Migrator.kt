package processm.core.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.postgresql.ds.PGSimpleDataSource
import processm.core.helpers.isUUID
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.util.*

/**
 * Database migrator.
 */
object Migrator {

    /**
     * Migrates the database to the current version using migration SQL scripts.
     * @link https://flywaydb.org/documentation/migrations
     */
    fun migrate() {
        logger().enter()

        logger().debug("Migrating database if required")
        System.setProperty(
            "PROCESSM.CORE.PERSISTENCE.CONNECTION.URL",
            "jdbc:postgresql://db.processm.cs.put.poznan.pl/db?user=bgorka&password=bgorka"
        )

        val connectionURL = System.getProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL")
        ensurePostgreSQLDatabase(connectionURL)
        val databaseName = ensureMainDBNameNotUUID(connectionURL)

        // Main database
        val conf = Flyway.configure().dataSource(connectionURL, null, null)
        conf.locations("db/processm_main_migrations")
        applyDefaultSchema(conf, connectionURL)
        conf.load().migrate()

        // DataSource databases
        val dbs = listOf("test")
        for (db in dbs) { // TODO: dbs from database - fetch UUIDs list
            val dbURL = switchDatabaseURL(connectionURL, databaseName, db)
            with(Flyway.configure().dataSource(dbURL, null, null)) {
                locations("db/processm_datastore_migrations")
                applyDefaultSchema(this, dbURL)
                load().migrate()
            }
        }

        logger().exit()
    }

    private fun switchDatabaseURL(connectionURL: String, mainDatabase: String, expectedDatabase: String): String {
        val withoutPSQL = connectionURL.substring(18)
        return "jdbc:postgresql://${Regex("/$mainDatabase").replace(withoutPSQL, "/$expectedDatabase")}"
    }

    private fun ensurePostgreSQLDatabase(connectionURL: String) {
        require(connectionURL.startsWith("jdbc:postgresql://")) { "Expected PostgreSQL database not found!" }
    }

    /**
     * Validate main database name - should not be in UUID format.
     * As result return database name.
     */
    private fun ensureMainDBNameNotUUID(connectionURL: String): String {
        val databaseName = with(PGSimpleDataSource()) {
            setURL(connectionURL)
            return@with databaseName!!
        }

        require(!databaseName.isUUID()) { "Database name can't be in UUID format" }

        return databaseName
    }

    /**
     * Workaround for a known bug in Flyway as of 2020-02-18.
     * @see https://github.com/flyway/flyway/issues/2182
     */
    private fun applyDefaultSchema(conf: FluentConfiguration, url: String) {
        val schema = Regex("defaultSchema=([^&]*)").find(url)?.groupValues?.get(1)
        conf.schemas(schema ?: "public")
    }
}