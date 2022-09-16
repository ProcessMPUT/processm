package processm.services.api

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.koin.test.KoinTest
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock
import processm.dbmodels.models.OrganizationRoleDto
import processm.services.api.models.AuthenticationResult
import processm.services.api.models.OrganizationRole
import processm.services.apiModule
import processm.services.logic.AccountService
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseApiTest : KoinTest {
    protected val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
            override fun write(out: JsonWriter, value: LocalDateTime?) {
                out.value(value?.toString())
            }

            override fun read(`in`: JsonReader): LocalDateTime = LocalDateTime.parse(`in`.nextString())
        }).create()

    protected abstract fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>?>
    protected abstract fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>?>
    private val mocksMap = mutableMapOf<KClass<*>, Any>()

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        // The resolved instances are cached, so every call to `declareMock` returns the same instance.
        MockProvider.register { mockedClass -> mocksMap.computeIfAbsent(mockedClass) { mockkClass(mockedClass) } }
    }

    @AfterTest
    fun tearDown() {
        clearAllMocks()
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
        testLogic(this)
    }

    protected fun TestApplicationEngine.withAuthentication(
        userId: UUID = UUID.randomUUID(),
        login: String = "user@example.com",
        password: String = "pass",
        role: Pair<OrganizationRole, UUID> = OrganizationRole.owner to UUID.randomUUID(),
        callback: JwtAuthenticationTrackingEngine.() -> Unit
    ) {
        val accountService = declareMock<AccountService>()
        every { accountService.verifyUsersCredentials(login, password) } returns mockk {
            every { id } returns userId
            every { email } returns login
        }
        every { accountService.getRolesAssignedToUser(userId) } returns
                listOf(mockk {
                    every { user.id } returns userId
                    every { organization.id } returns role.second
                    every { this@mockk.role } returns OrganizationRoleDto.byNameInDatabase(role.first.name)
                })

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
                    setBody("""{"login":"$login","password":"$password"}""")
                }) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    assertTrue(response.content!!.contains(AuthenticationResult::authorizationToken.name))
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
        // TODO: replace GSON with kotlinx/serialization
        val gsonBuilder = GsonBuilder()
        // Correctly serialize/deserialize LocalDateTime
        gsonBuilder.registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
            override fun write(out: JsonWriter, value: LocalDateTime?) {
                out.value(value?.toString())
            }

            override fun read(`in`: JsonReader): LocalDateTime = LocalDateTime.parse(`in`.nextString())
        })
        return gsonBuilder.create().fromJson(content, object : TypeToken<T>() {}.type)
    }

    protected inline fun <T : Any> TestApplicationRequest.withSerializedBody(requestBody: T) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(Gson().toJson(requestBody))
    }

}
