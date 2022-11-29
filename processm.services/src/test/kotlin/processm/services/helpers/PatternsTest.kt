package processm.services.helpers

import kotlin.test.*

class PatternsTest {
    @Test
    fun `valid email`() {
        assertTrue(Patterns.email.matches("user@example.com"))
        assertTrue(Patterns.email.matches("tpawlak@cs.put.poznan.pl"))
        assertTrue(Patterns.email.matches("account+label@gmail.com"))
    }

    @Test
    @Ignore("not implemented and no plans to implement yes")
    fun `unsupported valid email`() {
        assertTrue(Patterns.email.matches("root@localhost"))
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

    @Test
    fun `valid password`() {
        assertTrue(Patterns.password.matches("P@ssw0rd"))
        assertTrue(Patterns.password.matches("ABCD3FGHijKLMN"))
    }

    @Test
    fun `invalid password`() {
        assertFalse(Patterns.password.matches(""))
        assertFalse(Patterns.password.matches("Aa1"))
        assertFalse(Patterns.password.matches("!@#$%^&*()"))
        assertFalse(Patterns.password.matches("password"))
        assertFalse(Patterns.password.matches("\b\b\b\b\b\b\b\b"))
    }
}
