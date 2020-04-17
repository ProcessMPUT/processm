package processm.services.api

import org.junit.Test
import kotlin.test.assertFailsWith

class ApiUserTest {

    @Test
    fun `object creation throws if insufficient claims are provided`() {
        assertFailsWith<ApiException> {
            ApiUser(mapOf())
        }
    }

}
