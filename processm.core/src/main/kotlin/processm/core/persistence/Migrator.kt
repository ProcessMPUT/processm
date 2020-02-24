package processm.core.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger

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
        val url = System.getProperty("processm.core.persistence.connection.URL")
        val conf = Flyway.configure().dataSource(url, null, null)
        applyDefaultSchema(conf, url)
        val flyway = conf.load()
        flyway.migrate()

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