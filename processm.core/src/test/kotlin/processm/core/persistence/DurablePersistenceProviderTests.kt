package processm.core.persistence

import processm.core.persistence.connection.DBCache
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DurablePersistenceProviderTests : PersistenceProviderBaseTests() {

    init {
        DBCache.getMainDBPool().getConnection().use {
            it.createStatement().execute("DELETE FROM durable_storage WHERE urn LIKE 'urn:tests:myclass%'")
        }
    }

    @Test
    fun putGetDeleteTest() {
        DurablePersistenceProvider().use {
            putGetDeleteTest(it)
        }

        val urn2 = URI("urn:tests:myclass2")
        DurablePersistenceProvider().use {
            val obj2 = it.get<MyClass>(urn2)
            assertEquals("myclass2", obj2.field)
            it.delete(urn2)
            assertFailsWith<IllegalArgumentException> {
                it.delete(urn2)
            }
        }
    }
}
