package processm.core.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import processm.core.helpers.isUUID
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.loggedScope
import processm.core.logging.logger
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.DatabaseChecker.switchDatabaseURL
import java.sql.DriverManager

/**
 * Database migrator.
 */
object Migrator {
    private val dbConfig = DatabaseChecker

    init {
        // Required - we need `create_database` function in PostgreSQL main database
        migrateMainDatabase()
    }

    fun migrate(dataSourceDBName: String) {
        if (dataSourceDBName.isUUID()) migrateDataSourceDatabase(dataSourceDBName)
        else migrateMainDatabase()
    }

    /**
     * Migrates the main database to the current version using migration SQL scripts.
     * File stored at: `db/processm_main_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    private fun migrateMainDatabase() {
        logger().enter()
        logger().debug("Migrating main database if required")

        with(Flyway.configure().dataSource(dbConfig.baseConnectionURL, null, null)) {
            locations("db/processm_main_migrations")
            applyDefaultSchema(this, dbConfig.baseConnectionURL)
            load().migrate()
        }

        logger().exit()
    }

    /**
     * Migrates the datasource database passed by name to the current version using migration SQL scripts.
     * File stored at: `db/processm_datastore_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    private fun migrateDataSourceDatabase(dataSourceDBName: String) {
        loggedScope { logger ->
            logger.debug("Migrating datasource database if required")

            ensureDatabaseExists(dataSourceDBName)
            val expectedDatabaseConnectionURL = switchDatabaseURL(dataSourceDBName)

            with(Flyway.configure().dataSource(expectedDatabaseConnectionURL, null, null)) {
                locations("db/processm_datastore_migrations")
                applyDefaultSchema(this, expectedDatabaseConnectionURL)
                load().migrate()
            }

        }
    }

    private fun ensureDatabaseExists(dataSourceDBName: String) {
        loggedScope { logger ->
            logger.debug("Create datasource database if required")

            require(dataSourceDBName.isUUID()) { "Datasource DB should be named with UUID." }

            DriverManager.getConnection(dbConfig.baseConnectionURL).prepareStatement(
                """
            SELECT * FROM create_database(?);
            """.trimIndent()
            ).use {
                it.setString(1, dataSourceDBName)
                it.executeQuery().use { result ->
                    require(result.next()) { "Database cannot be created" }
                    require(result.getBoolean("create_database")) { "Database cannot be created" }
                }
            }
        }
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