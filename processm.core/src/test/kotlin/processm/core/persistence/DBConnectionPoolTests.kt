package processm.core.persistence

import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.helpers.loadConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class DBConnectionPoolTests {
    init {
        loadConfiguration()
    }

    @Test
    fun getConnectionTest() {
        DBConnectionPool.getConnection().use {
            assertEquals(true, it.isValid(3))
        }
    }

    @Test
    fun getDataSourceTest() {
        DBConnectionPool.getDataSource().connection.use {
            assertEquals(true, it.isValid(3))
        }
    }

    @Test
    fun databaseTest() {
        transaction(DBConnectionPool.database) {
            assertEquals(false, this.db.connector().isClosed)
        }
    }
}