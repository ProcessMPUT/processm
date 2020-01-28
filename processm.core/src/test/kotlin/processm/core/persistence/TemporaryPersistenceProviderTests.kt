package processm.core.persistence

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlin.test.Test
import processm.core.helpers.loadConfiguration
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ImplicitReflectionSerializer
class TemporaryPersistenceProviderTests : PersistenceProviderBaseTests() {
    init {
        loadConfiguration()
        DBConnectionPool.getConnection().use{
            it.createStatement().execute("DELETE FROM durable_storage WHERE urn LIKE 'urn:tests:myclass%'")
        }
    }

    @Test
    fun putGetDeleteTest() {
        TemporaryPersistenceProvider().use {
            putGetDeleteTest(it)

            TemporaryPersistenceProvider().use {nested ->
                putGetDeleteTest(nested)
            }
        }

        val urn2 = URI("urn:tests:myclass2")
        TemporaryPersistenceProvider().use {
            assertFailsWith<IllegalArgumentException> {
                val obj2 = it.get<MyClass>(urn2)
                print(obj2)
            }
        }
    }
}