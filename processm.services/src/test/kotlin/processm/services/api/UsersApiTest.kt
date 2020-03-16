package processm.services.api

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        Pair(HttpMethod.Get, "/api/users"),
        Pair(HttpMethod.Delete, "/api/users/session"),
        Pair(HttpMethod.Get, "/api/users/me")
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        Pair(HttpMethod.Get, "/api/users"),
        Pair(HttpMethod.Post, "/api/users"),
        Pair(HttpMethod.Delete, "/api/users/session"),
        Pair(HttpMethod.Get, "/api/users/me")
    )

    @Test
    fun `responds to successful authentication with 201 and token`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"user","password":"pass"}""")}) {
            assertEquals(HttpStatusCode.Created, response.status())
            assertTrue(response.content!!.contains("accessToken"))
        }
    }

    @Test
    fun `responds to unsuccessful authentication with 401 and error message`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"user","password":"wrong_password"}""")}) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertFalse(response.content!!.contains("accessToken"))
            assertTrue(response.content!!.contains(""""error":"Invalid username or password""""))
        }
    }

}
