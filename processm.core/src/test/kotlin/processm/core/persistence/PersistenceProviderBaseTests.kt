package processm.core.persistence

import kotlinx.serialization.Serializable
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

open class PersistenceProviderBaseTests {
    fun putGetDeleteTest(provider: PersistenceProvider) {
        val urn1 = URI("urn:tests:myclass1")
        val urn2 = URI("urn:tests:myclass2")
        val obj1 = MyClass("myclass1")
        val obj2 = MyClass("myclass2")
        val obj3 = MyClass("myclass3")

        assertFailsWith<IllegalArgumentException> {
            provider.get<MyClass>(urn1)
        }
        assertFailsWith<IllegalArgumentException> {
            provider.get<MyClass>(urn2)
        }

        provider.put(urn1, obj1)
        provider.put(urn2, obj2)

        // Note: DO NOT use assertEquals(obj1, provider.get(...)), as provider is free to return a new object. Only
        // the content of the returned object matters and this is compared using == operator.
        assertEquals(obj1, provider.get<MyClass>(urn1))
        assertEquals(obj2, provider.get<MyClass>(urn2))
        assertNotEquals(obj1, provider.get<MyClass>(urn2))
        assertNotEquals(obj2, provider.get<MyClass>(urn1))

        provider.put(urn1, obj3)
        assertEquals(obj3, provider.get<MyClass>(urn1))
        assertNotEquals(obj1, provider.get<MyClass>(urn1))

        provider.delete(urn1)
        assertFailsWith<IllegalArgumentException> {
            provider.get<MyClass>(urn1)
        }
        assertEquals(obj2, provider.get<MyClass>(urn2))
        assertNotEquals(obj1, provider.get<MyClass>(urn2))

        assertFailsWith<IllegalArgumentException> {
            provider.delete(urn1)
        }
    }
}

@Serializable
data class MyClass(val field: String) {

}