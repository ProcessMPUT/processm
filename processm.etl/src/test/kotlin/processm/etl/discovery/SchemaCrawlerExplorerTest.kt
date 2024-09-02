package processm.etl.discovery

import processm.core.persistence.connection.DatabaseChecker
import java.sql.DriverManager
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaCrawlerExplorerTest {

    private lateinit var db: String

    @BeforeTest
    fun `create db`() {
        db = UUID.randomUUID().toString()
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("create database \"$db\"").use { it.execute() }
        }
    }

    @AfterTest
    fun `drop db`() {
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("drop database if exists \"$db\"").use { it.execute() }
        }
    }

    @Test
    fun `timescaledb partitions are not listed`() {
        DriverManager.getConnection(DatabaseChecker.switchDatabaseURL(db)).use { connection ->
            connection.createStatement().use {
                it.execute(
                    """CREATE EXTENSION IF NOT EXISTS timescaledb;
                    CREATE TABLE events
                    (
                        id                     BIGSERIAL PRIMARY KEY,
                        text    text
                    );
                    SELECT create_hypertable('events', by_range('id', 10), if_not_exists => TRUE, migrate_data => true);
                """
                )
            }
            val query = (1..200).joinToString(separator = ",") { "($it, '$it')" }
            connection.prepareStatement("insert into events values $query").use { stmt ->
                stmt.executeUpdate()
            }
        }
        DriverManager.getConnection(DatabaseChecker.switchDatabaseURL(db)).use { connection ->
            val explorer = SchemaCrawlerExplorer(connection)
            with(explorer.getClasses()) {
                assertEquals(1, size)
                with(single()) {
                    assertEquals("public", schema)
                    assertEquals("events", name)
                }
            }
        }
    }
}
