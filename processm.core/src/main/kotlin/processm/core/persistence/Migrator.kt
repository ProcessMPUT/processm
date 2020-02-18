package processm.core.persistence

import org.flywaydb.core.Flyway
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
        val flyway = Flyway.configure().dataSource(url, null, null).load()
        flyway.migrate()

        logger().exit()
    }
}