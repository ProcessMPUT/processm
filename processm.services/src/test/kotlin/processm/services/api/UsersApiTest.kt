package processm.services.api

import io.ktor.config.MapApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.TestInstance
import processm.services.apiModule
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/users",
        HttpMethod.Delete to "/api/users/session",
        HttpMethod.Get to "/api/users/me"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Get to "/api/users",
        HttpMethod.Post to "/api/users",
        HttpMethod.Delete to "/api/users/session",
        HttpMethod.Get to "/api/users/me"
    )

    @Test
    fun `responds to successful authentication with 201 and token`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"user","password":"pass"}""")
        }) {
            assertEquals(HttpStatusCode.Created, response.status())
            assertTrue(response.content!!.contains("accessToken"))
        }
    }

    @Test
    fun `responds to unsuccessful authentication with 401 and error message`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"user","password":"wrong_password"}""")
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertFalse(response.content!!.contains("accessToken"))
            assertTrue(response.content!!.contains(""""error":"Invalid username or password""""))
        }
    }

    @Test
    fun `responds to request with expired token with 401`() = withTestApplication {
        // token expires one second after creation
        (environment.config as MapApplicationConfig).apply {
            put("ktor.jwt.issuer", "issuer")
            put("ktor.jwt.realm", "test")
            put("ktor.jwt.secret", "secretkey123")
            put("ktor.jwt.tokenTtl", "PT1S")
        }
        application.apiModule()

        withAuthentication {
            // wait till the current token expires
            await().until {
                with(handleRequest(HttpMethod.Get, "/api/users")) {
                    response.status() == HttpStatusCode.Unauthorized
                }
            }

            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `responds to authentication request with valid expired token with 201 and renewed token`() = withTestApplication {
        // token expires two seconds after creation
        (environment.config as MapApplicationConfig).apply {
            put("ktor.jwt.issuer", "issuer")
            put("ktor.jwt.realm", "test")
            put("ktor.jwt.secret", "secretkey123")
            put("ktor.jwt.tokenTtl", "PT2S")
        }
        application.apiModule()

        var renewedToken: String? = null

        withAuthentication {
            // wait till the current token expires
            await().until {
                with(handleRequest(HttpMethod.Get, "/api/users")) {
                    response.status() == HttpStatusCode.Unauthorized
                }
            }

            // make sure the token is expired
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }

            // renew the token
            with(handleRequest(HttpMethod.Post, "/api/users/session")) {
                assertEquals(HttpStatusCode.Created, response.status())
                assertTrue(response.content!!.contains("accessToken"))
                renewedToken = response.content!!.substringAfter("""accessToken":"""").substringBefore('"')
            }
        }

        assertNotNull(renewedToken)

        // make sure the token is valid
        with(handleRequest(HttpMethod.Get, "/api/users"){
            addHeader(HttpHeaders.Authorization, "Bearer $renewedToken")
        }) {
            assertNotEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds to authentication request without credentials or token with 400 and error message`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session")) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
            assertTrue(response.content!!.contains("accessToken"))
        }
    }
}
