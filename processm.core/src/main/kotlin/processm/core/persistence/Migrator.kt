package processm.core.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import processm.core.persistence.connection.DatabaseChecker.ensureMainDBNameNotUUID
import processm.core.persistence.connection.DatabaseChecker.ensurePostgreSQLDatabase
import processm.core.persistence.connection.DatabaseChecker.switchDatabaseURL
import kotlin.properties.Delegates

/**
 * Database migrator.
 */
object Migrator {
    internal var baseConnectionURL: String by Delegates.notNull()
    private lateinit var mainDatabaseName: String

    /**
     * Migrates the main database to the current version using migration SQL scripts.
     * File stored at: `db/processm_main_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    fun migrateMainDatabase() {
        logger().enter()
        logger().debug("Migrating main database if required")

        ensurePostgreSQLDatabase(baseConnectionURL)
        mainDatabaseName = ensureMainDBNameNotUUID(baseConnectionURL)

        with(Flyway.configure().dataSource(baseConnectionURL, null, null)) {
            locations("db/processm_main_migrations")
            applyDefaultSchema(this, baseConnectionURL)
            load().migrate()
        }

        logger().exit()
    }

    /**
     * Migrates the datasource database passed by name to the current version using migration SQL scripts.
     * File stored at: `db/processm_datastore_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    fun migrateDataSourceDatabase(expectedDatabase: String) {
        logger().enter()
        logger().debug("Migrating datasource database if required")

        val expectedDatabaseConnectionURL = switchDatabaseURL(baseConnectionURL, mainDatabaseName, expectedDatabase)

        with(Flyway.configure().dataSource(expectedDatabaseConnectionURL, null, null)) {
            locations("db/processm_datastore_migrations")
            applyDefaultSchema(this, expectedDatabaseConnectionURL)
            load().migrate()
        }

        logger().exit()
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