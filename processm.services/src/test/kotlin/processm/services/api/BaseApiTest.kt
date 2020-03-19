package processm.services.api

import io.ktor.config.MapApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import processm.services.apiModule
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class BaseApiTest {

    protected abstract fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>>
    protected abstract fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>>


    @ParameterizedTest
    @MethodSource("endpointsWithAuthentication")
    fun `responds to not authenticated requests with 403`(requestEndpoint: Pair<HttpMethod, String>) = withConfiguredTestApplication {
        val (method, path) = requestEndpoint
        with(handleRequest(method, path)) {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @ParameterizedTest
    @MethodSource("endpointsWithNoImplementation")
    fun `responds to not implemented requests with 501`(requestEndpoint: Pair<HttpMethod, String>) = withConfiguredTestApplication {
        withAuthentication {
            val (method, path) = requestEndpoint
            with(handleRequest(method, path)) {
                assertEquals(HttpStatusCode.NotImplemented, response.status())
            }
        }
    }

    protected fun <R> withConfiguredTestApplication(test: TestApplicationEngine.() -> R): R = withTestApplication {
        (environment.config as MapApplicationConfig).apply {
            put("ktor.jwt.issuer", "issuer")
            put("ktor.jwt.realm", "test")
            put("ktor.jwt.secret", "secretkey123")
            put("ktor.jwt.tokenTtl", "PT10S")
        }
        application.apiModule()
        test(this)
    }

    protected fun TestApplicationEngine.withAuthentication(
        username: String = "user",
        password: String = "pass",
        callback: JwtAuthenticationTrackingEngine.() -> Unit) =
        callback(JwtAuthenticationTrackingEngine(this, username, password))

    protected class JwtAuthenticationTrackingEngine(
        private val engine: TestApplicationEngine,
        private val username: String,
        private val password: String) {

        private var authenticationHeader: Pair<String, String> = Pair("", "")

        fun handleRequest(
            method: HttpMethod,
            uri: String,
            test: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
            with(engine.handleRequest(HttpMethod.Post, "/api/users/session") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"username":"$username","password":"$password"}""")}) {
                assertEquals(HttpStatusCode.Created, response.status())
                assertTrue(response.content!!.contains("accessToken"))

                val token = response.content?.substringAfter("""accessToken":"""")?.substringBefore('"')
                authenticationHeader = Pair("Authorization", "Bearer $token")
            }

            return engine.handleRequest(method, uri) {
                if (!authenticationHeader.first.isNullOrEmpty()) {
                    addHeader(authenticationHeader.first, authenticationHeader.second)
                }
                test()
            }
        }
    }
}
