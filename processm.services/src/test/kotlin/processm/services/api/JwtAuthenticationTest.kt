package processm.services.api

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JwtAuthenticationTest {

    @Test
    fun `random secret is not constant`() {
        assertNotEquals(JwtAuthentication.generateSecretKey(), JwtAuthentication.generateSecretKey())
    }

    @Test
    fun `secret is constant within an execution`() {
        val first = JwtAuthentication.getSecretKey(mockk {
            every { propertyOrNull("secret") } returns null
        })
        val second = JwtAuthentication.getSecretKey(mockk {
            every { propertyOrNull("secret") } returns null
        })
        assertEquals(first, second)
    }
}