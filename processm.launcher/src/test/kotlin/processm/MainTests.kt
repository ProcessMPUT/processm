package processm

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MainTests {

    @Test
    fun assertionsEnabled() {
        assertFailsWith<AssertionError> {
            assert(false) { "Enable JVM assertions in tests using -ea parameter." }
        }
    }
}