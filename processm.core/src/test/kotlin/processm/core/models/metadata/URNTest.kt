package processm.core.models.metadata


import processm.helpers.ExceptionReason
import processm.helpers.LocalizedException
import kotlin.test.*

class URNTest {

    @Test
    fun rfc8141() {
        URN("urn:example:a123,z456")
        URN("URN:example:a123,z456")
        URN("urn:EXAMPLE:a123,z456")
        URN("urn:example:a123,z456?+abc")
        URN("urn:example:a123,z456?=xyz")
        URN("urn:example:a123,z456#789")
        URN("urn:example:a123,z456/foo")
        URN("urn:example:a123,z456/bar")
        URN("urn:example:a123,z456/baz")
        URN("urn:example:a123%2Cz456")
        URN("URN:EXAMPLE:a123%2cz456")
        URN("urn:example:A123,z456")
        URN("urn:example:a123,Z456")
        URN("urn:example:%D0%B0123,z456")
        URN("urn:example:apple:pear:plum:cherry")
    }

    @Test
    fun testEquals() {
        assertTrue { URN("urn:example:a123,z456") == URN("urn:example:a123,z456") }
        assertFalse { URN("urn:example:a123,z456") == URN("urn:example:a123,z456/foo") }
    }

    @Test
    fun testHashCode() {
        assertEquals(URN("urn:example:a123").hashCode(), URN("urn:example:a123").hashCode())
    }

    @Test
    fun invalidNID() {
        assertFailsWith(LocalizedException::class) { URN("urn:_example:a123") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
        assertFailsWith(LocalizedException::class) { URN("urn:example_:a123") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
        assertFailsWith(LocalizedException::class) { URN("urn:exampleexampleexampleexampleexampleexampleexample:a123") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
    }

    @Test
    fun invalidNSS() {
        assertFailsWith(LocalizedException::class) { URN("urn:example:/a123bcd") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
        assertFailsWith(LocalizedException::class) { URN("urn:example:") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
    }

    @Test
    fun invalidPrefix() {
        assertFailsWith(LocalizedException::class) { URN("NRU:example:a123") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
        assertFailsWith(LocalizedException::class) { URN(":example:a123") }.also {
            assertEquals(ExceptionReason.NotAValidURN, it.reason)
        }
    }

}