package processm.services.api

import io.ktor.config.MapApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.*
import processm.services.apiModule
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/users",
        HttpMethod.Delete to "/api/users/session",
        HttpMethod.Get to "/api/users/me"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/users",
        HttpMethod.Delete to "/api/users/session"
    )

    @Test
    fun `responds to successful authentication with 201 and token`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentialsMessageBody(UserCredentials("user", "pass")))
        }) {
            assertEquals(HttpStatusCode.Created, response.status())
            assertTrue(response.deserializeContent<AuthenticationResultMessageBody>().data.authorizationToken.isNotBlank())
        }
    }

    @Test
    fun `responds to unsuccessful authentication with 401 and error message`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            withSerializedBody(UserCredentialsMessageBody(UserCredentials("user", "wrong_password")))
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
            assertTrue(response.deserializeContent<ErrorMessageBody>().error.contains("Invalid username or password"))
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
                renewedToken = assertNotNull(response.deserializeContent<AuthenticationResultMessageBody>().data.authorizationToken)
            }
        }

        assertNotNull(renewedToken)

        // make sure the token is valid
        with(handleRequest(HttpMethod.Get, "/api/users") {
            addHeader(HttpHeaders.Authorization, "Bearer $renewedToken")
        }) {
            assertNotEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds to authentication request without credentials or token with 400 and error message`() = withConfiguredTestApplication {
        with(handleRequest(HttpMethod.Post, "/api/users/session")) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
            assertTrue(
                response.deserializeContent<ErrorMessageBody>().error
                    .contains("Either user credentials or authentication token needs to be provided"))
        }
    }

    @Test
    fun `responds to request with malformed token with 401`() = withConfiguredTestApplication {
        var currentToken: String? = null

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertNotEquals(HttpStatusCode.Unauthorized, response.status())
                currentToken = request.header(HttpHeaders.Authorization)
            }
        }

        var randomizedToken = StringBuilder(assertNotNull(currentToken))

        do {
            repeat(20) {
                randomizedToken[Random.nextInt(randomizedToken.indices)] = ('A'..'z').random()
            }
        } while (randomizedToken.toString() == currentToken)

        with(handleRequest(HttpMethod.Get, "/api/users") {
            addHeader(HttpHeaders.Authorization, "Bearer $randomizedToken")
        }) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `responds with 200 and users list`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<UserInfoCollectionMessageBody>().data)
            }
        }
    }

    @Test
    fun `responds with 200 and current user account details`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/users/me")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val deserializedContent = response.deserializeContent<UserAccountInfoMessageBody>()
                assertEquals("user", deserializedContent.data.username)
                assertEquals(OrganizationRole.owner, deserializedContent.data.organizationRoles!!["org1"])
            }
        }
    }
}
