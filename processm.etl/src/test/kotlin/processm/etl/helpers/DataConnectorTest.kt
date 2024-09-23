package processm.etl.helpers

import org.jgroups.util.UUID
import org.junit.jupiter.api.assertThrows
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.DatabaseChecker
import processm.core.persistence.connection.transaction
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.*

/**
 * Every test runs in a separate DB, because otherwise transaction managers interfered between tests - every test worked
 * correctly when run on its own, but when they were run jointly (i.e., a test runner was to run the whole class), they failed
 */
class DataConnectorTest {
    private lateinit var dbName: String

    @BeforeTest
    fun setup() {
        dbName = UUID.randomUUID().toString()

        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("create database \"$dbName\"").use {
                it.execute()
            }
        }
    }

    @AfterTest
    fun teardown() {
        DBCache.get(dbName).close()
        DriverManager.getConnection(DatabaseChecker.baseConnectionURL).use { connection ->
            connection.prepareStatement("drop database \"$dbName\"").use {
                it.execute()
            }
        }
    }

    @Test
    fun `lastConnectionStatus is updated on failed connection`() {
        val dc = transaction(dbName) {
            DataConnector.new {
                name = "some name"
                connectionProperties = ConnectionProperties(ConnectionType.JdbcString, "jdbc:invalid://")
            }
        }
        assertNull(dc.lastConnectionStatus)
        assertNull(dc.lastConnectionStatusTimestamp)
        assertThrows<SQLException> { dc.getConnection() }
        transaction(dbName) {
            with(DataConnector.findById(dc.id)) {
                assertNotNull(this)
                assertEquals(false, this.lastConnectionStatus)
                assertNotNull(this.lastConnectionStatusTimestamp)
            }
        }
    }

    @Test
    fun `lastConnectionStatus is updated on successful connection`() {
        val dc = transaction(dbName) {
            DataConnector.new {
                name = "other name"
                connectionProperties =
                    ConnectionProperties(ConnectionType.JdbcString, DatabaseChecker.baseConnectionURL)
            }
        }
        assertNull(dc.lastConnectionStatus)
        assertNull(dc.lastConnectionStatusTimestamp)
        dc.getConnection().close()
        transaction(dbName) {
            with(DataConnector.findById(dc.id)) {
                assertNotNull(this)
                assertEquals(true, this.lastConnectionStatus)
                assertNotNull(this.lastConnectionStatusTimestamp)
            }
        }
    }

    @Test
    fun `lastConnectionStatus is updated on successful connection even if theres another transaction in between`() {
        val dc = transaction(dbName) {
            DataConnector.new {
                name = "other name"
                connectionProperties =
                    ConnectionProperties(ConnectionType.JdbcString, DatabaseChecker.baseConnectionURL)
            }
        }
        assertNull(dc.lastConnectionStatus)
        assertNull(dc.lastConnectionStatusTimestamp)
        transactionMain {
            commit()
        }
        dc.getConnection().close()
        transaction(dbName) {
            with(DataConnector.findById(dc.id)) {
                assertNotNull(this)
                assertEquals(true, this.lastConnectionStatus)
                assertNotNull(this.lastConnectionStatusTimestamp)
            }
        }
    }
}