package processm.core.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.DatabaseChecker.switchDatabaseURL
import processm.helpers.isUUID
import processm.logging.loggedScope
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

    /**
     * Ensures that the DB used is the one given in the configuration by calling [DatabaseChecker.reloadConfiguration] and migrates the main DB
     */
    fun reloadConfiguration() {
        dbConfig.reloadConfiguration()
        migrateMainDatabase()
    }

    fun migrate(dataStoreDBName: String) {
        if (dataStoreDBName.isUUID()) migrateDataStoreDatabase(dataStoreDBName)
        else migrateMainDatabase()
    }

    /**
     * Migrates the main database to the current version using migration SQL scripts.
     * File stored at: `db/processm_main_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    private fun migrateMainDatabase() {
        loggedScope { logger ->
            logger.info("Migrating the main database if required")

            with(Flyway.configure().dataSource(dbConfig.baseConnectionURL, null, null)) {
                locations("db/processm_main_migrations")
                applyDefaultSchema(this, dbConfig.baseConnectionURL)
                load().migrate()
            }

        }
    }

    /**
     * Migrates the datastore database passed by name to the current version using migration SQL scripts.
     * File stored at: `db/processm_datastore_migrations`.
     * @link https://flywaydb.org/documentation/migrations
     */
    private fun migrateDataStoreDatabase(dataStoreDBName: String) {
        loggedScope { logger ->
            logger.info("Migrating datastore $dataStoreDBName if required")

            ensureDatabaseExists(dataStoreDBName)
            val expectedDatabaseConnectionURL = switchDatabaseURL(dataStoreDBName)

            with(Flyway.configure().dataSource(expectedDatabaseConnectionURL, null, null)) {
                locations("db/processm_datastore_migrations")
                applyDefaultSchema(this, expectedDatabaseConnectionURL)
                load().migrate()
            }

        }
    }

    private fun jdbc2libpq(jdbc: String): String {
        // https://www.postgresql.org/docs/15/libpq-connect.html#LIBPQ-CONNSTRING
        if (jdbc.startsWith("jdbc:postgres"))   // The URI scheme designator can be either postgresql:// or postgres://.
            return jdbc.substring(5)
        else
            throw IllegalArgumentException("Cannot deal with '$jdbc'")
    }

    private fun ensureDatabaseExists(dataStoreDBName: String) {
        loggedScope { logger ->
            logger.debug("Create datastore database if required")

            require(dataStoreDBName.isUUID()) { "Datastore DB should be named with UUID." }

            DriverManager.getConnection(dbConfig.baseConnectionURL).use { connection ->
                connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use { dbExists ->
                    dbExists.setString(1, dataStoreDBName)
                    if (dbExists.executeQuery().use { it.next() }) {
                        logger.info("Database $dataStoreDBName already exists")
                    } else {
                        //Since dataStoreDBName is a UUID, it is safe to do it this way
                        connection.prepareStatement("CREATE DATABASE \"$dataStoreDBName\"").use {
                            it.execute()
                        }
                    }
                    require(dbExists.executeQuery().use { it.next() }) { "Database cannot be created" }
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
