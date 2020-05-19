package processm.core.querylanguage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiteralTests {

    @Test
    fun emptyStringTest() {
        val literal = StringLiteral("\"\"", 0, 0)

        assertNull(literal.scope)
        assertEquals(Scope.Event, literal.effectiveScope)
        assertTrue(literal.isTerminal)
        assertEquals("", literal.value)
    }

    @Test
    fun escapeCharInStringTest() {
        val literal = StringLiteral("\"abc jr\\\"\"", 0, 0)

        assertNull(literal.scope)
        assertEquals(Scope.Event, literal.effectiveScope)
        assertTrue(literal.isTerminal)
        assertEquals("abc jr\"", literal.value)
    }

    @Test
    fun escapeSequenceAtTheEndOfStringTest() {
        val literal = StringLiteral("\"abc jr\\\"", 0, 0)

        assertNull(literal.scope)
        assertEquals(Scope.Event, literal.effectiveScope)
        assertTrue(literal.isTerminal)
        assertEquals("abc jr", literal.value)
    }

    @Test
    fun specialEscapeCharactersTest() {
        val literal = StringLiteral("\"\\t\\r\\n\\f\\b\\\\\"", 0, 0)

        assertNull(literal.scope)
        assertEquals(Scope.Event, literal.effectiveScope)
        assertTrue(literal.isTerminal)
        assertEquals("\t\r\n" + 12.toChar() + "\b\\", literal.value)
    }
}