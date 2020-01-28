package processm.core.persistence

import org.flywaydb.core.Flyway

/**
 * Database migrator.
 */
object Migrator {

    /**
     * Migrates the database to the current version using migration SQL scripts.
     * @link https://flywaydb.org/documentation/migrations
     */
    fun migrate() {
        val url = System.getProperty("processm.core.persistence.connection.URL")
        val flyway = Flyway.configure().dataSource(url, null, null).load()
        flyway.migrate()
    }
}