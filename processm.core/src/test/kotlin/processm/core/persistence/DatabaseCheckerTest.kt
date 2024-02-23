package processm.core.persistence

import org.junit.jupiter.api.assertThrows
import processm.core.persistence.connection.DatabaseChecker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseCheckerTest {

    private lateinit var previousValue: List<Map.Entry<Any, Any>>

    @BeforeTest
    fun setup() {
        DatabaseChecker.reloadConfiguration()
        previousValue = System.getProperties().entries.filter {
            DatabaseChecker.reservedConnectionsPropertyName.equals(it.key as String, true)
        }
        for (entry in previousValue)
            System.clearProperty(entry.key.toString())
    }

    @AfterTest
    fun takedown() {
        System.clearProperty(DatabaseChecker.reservedConnectionsPropertyName)
        for (entry in previousValue)
            System.setProperty(entry.key.toString(), entry.value.toString())
        DatabaseChecker.reloadConfiguration()
    }

    @Test
    fun `fail if all the connections are reserved`() {
        System.setProperty(DatabaseChecker.reservedConnectionsPropertyName, Integer.MAX_VALUE.toString())
        assertThrows<IllegalStateException> { DatabaseChecker.reloadConfiguration() }
    }

    @Test
    fun `reservations are taken into account`() {
        System.setProperty(DatabaseChecker.reservedConnectionsPropertyName, "0")
        DatabaseChecker.reloadConfiguration()
        val n1 = DatabaseChecker.maxPoolSize
        System.setProperty(DatabaseChecker.reservedConnectionsPropertyName, "1")
        DatabaseChecker.reloadConfiguration()
        val n2 = DatabaseChecker.maxPoolSize
        assertEquals(1, n1 - n2)
    }
}