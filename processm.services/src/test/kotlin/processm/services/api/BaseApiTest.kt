package processm.services.api

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import io.ktor.config.MapApplicationConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.*
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.Before
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.koin.test.AutoCloseKoinTest
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock
import processm.services.api.models.AuthenticationResult
import processm.services.apiModule
import processm.services.logic.AccountService
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class BaseApiTest : AutoCloseKoinTest() {

    protected abstract fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>?>
    protected abstract fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>?>

    // @Before causes the setUp() method to be called when running tests individually
    // @BeforeEach causes the setUp() method to be called before @ParameterizedTest tests
    @Before
    @BeforeEach
    open fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        MockProvider.register { mockedClass -> mockkClass(mockedClass) }
    }

    @ParameterizedTest
    @MethodSource("endpointsWithAuthentication")
    fun `responds to not authenticated requests with 403`(requestEndpoint: Pair<HttpMethod, String>?) =
        withConfiguredTestApplication {
            if (requestEndpoint == null) {
                return@withConfiguredTestApplication
            }

            val (method, path) = requestEndpoint
            with(handleRequest(method, path)) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }

    @ParameterizedTest
    @MethodSource("endpointsWithNoImplementation")
    fun `responds to not implemented requests with 501`(requestEndpoint: Pair<HttpMethod, String>?) =
        withConfiguredTestApplication {
            if (requestEndpoint == null) {
                return@withConfiguredTestApplication
            }

            withAuthentication {
                val (method, path) = requestEndpoint
                with(handleRequest(method, path)) {
                    assertEquals(HttpStatusCode.NotImplemented, response.status())
                }
            }
        }

    protected lateinit var accountService: AccountService

    protected fun <R> withConfiguredTestApplication(
        configurationCustomization: (MapApplicationConfig.() -> Unit)? = null,
        testLogic: TestApplicationEngine.() -> R
    ): R = withTestApplication {
        val configuration = (environment.config as MapApplicationConfig).apply {
            put("ktor.jwt.issuer", "issuer")
            put("ktor.jwt.realm", "test")
            put("ktor.jwt.secret", "secretkey123")
            put("ktor.jwt.tokenTtl", "PT10S")
        }
        configurationCustomization?.invoke(configuration)

        application.apiModule()
        accountService = declareMock { }

        testLogic(this)
    }

    protected fun TestApplicationEngine.withAuthentication(
        login: String = "user@example.com", password: String = "pass", callback: JwtAuthenticationTrackingEngine.() -> Unit
    ) {
        every { accountService.verifyUsersCredentials(login, password) } returns mockk {
            every { id } returns EntityID<UUID>(UUID.randomUUID(), mockk())
            every { email } returns login
        }

        callback(JwtAuthenticationTrackingEngine(this, login, password))
    }

    protected class JwtAuthenticationTrackingEngine(
        private val engine: TestApplicationEngine, private val login: String, private val password: String
    ) {

        private var authenticationHeader: Pair<String, String>? = null

        fun handleRequest(
            method: HttpMethod, uri: String, test: TestApplicationRequest.() -> Unit = {}
        ): TestApplicationCall {
            if (authenticationHeader == null) {
                with(engine.handleRequest(HttpMethod.Post, "/api/users/session") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"data":{"login":"$login","password":"$password"}}""")
                }) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    assertTrue(response.content!!.contains("${AuthenticationResult::authorizationToken.name}"))
                    val token =
                        response.content?.substringAfter("""${AuthenticationResult::authorizationToken.name}":"""")
                            ?.substringBefore('"')
                    authenticationHeader = Pair(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            return engine.handleRequest(method, uri) {
                if (authenticationHeader != null && !authenticationHeader?.first.isNullOrEmpty()) {
                    addHeader(authenticationHeader!!.first, authenticationHeader?.second ?: "")
                }
                test()
            }
        }
    }

    protected inline fun <reified T> TestApplicationResponse.deserializeContent(): T {
        return Gson().fromJson(content, object : TypeToken<T>() {}.type)
    }

    protected inline fun <T : Any> TestApplicationRequest.withSerializedBody(requestBody: T) {
        setBody(Gson().toJson(requestBody))
    }

}
