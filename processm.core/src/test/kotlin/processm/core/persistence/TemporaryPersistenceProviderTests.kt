package processm.core.persistence

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TemporaryPersistenceProviderTests : PersistenceProviderBaseTests() {
    @Test
    fun putGetDeleteTest() {
        TemporaryPersistenceProvider().use {
            putGetDeleteTest(it)

            TemporaryPersistenceProvider().use { nested ->
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
