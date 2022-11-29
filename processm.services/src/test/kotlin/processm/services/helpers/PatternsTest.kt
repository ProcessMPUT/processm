package processm.services.helpers

import kotlin.test.*

class PatternsTest {
    @Test
    fun `valid email`() {
        assertTrue(Patterns.email.matches("user@example.com"))
        assertTrue(Patterns.email.matches("tpawlak@cs.put.poznan.pl"))
    }

    @Test
    @Ignore("not implemented and no plans to implement yes")
    fun `unsupported valid email`() {
        assertTrue(Patterns.email.matches("root@localhost"))
        assertTrue(Patterns.email.matches("account+label@gmail.com"))
    }

    @Test
    fun `invalid email`() {
        assertFalse(Patterns.email.matches(""))
        assertFalse(Patterns.email.matches("userexample.com"))
        assertFalse(Patterns.email.matches("!@#$%^&*()"))
        assertFalse(Patterns.email.matches("account@"))
        assertFalse(Patterns.email.matches("@example.com"))
        assertFalse(Patterns.email.matches("user@example.com?subject=xxx"))
    }
}
