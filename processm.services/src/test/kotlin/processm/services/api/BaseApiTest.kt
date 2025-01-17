package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Table
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.koin.test.KoinTest
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import processm.services.JsonSerializer
import processm.services.api.models.AuthenticationResult
import processm.services.api.models.OrganizationRole
import processm.services.apiModule
import processm.services.helpers.ExceptionReason
import processm.services.logic.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.*

class ACLDSL {
    val entries = ArrayList<Triple<RoleType, Table, UUID>>()

    operator fun RoleType.times(other: Table) = this to other
    operator fun Pair<RoleType, Table>.times(other: UUID): ACLDSL {
        entries.add(Triple(this.first, this.second, other))
        return this@ACLDSL
    }

    operator fun ACLDSL.plus(acl: ACLDSL) = acl
}

fun acl(block: ACLDSL.() -> Unit): List<Triple<RoleType, Table, UUID>> = with(ACLDSL()) {
    block()
    return entries
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseApiTest : KoinTest {
    protected abstract fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>?>
    protected abstract fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>?>
    private val mocksMap = ConcurrentHashMap<KClass<*>, Any>()

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
    fun `responds to not authenticated requests with 401`(requestEndpoint: Pair<HttpMethod, String>?) =
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
    @Ignore("No not implemented endpoints")
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

    protected inline fun TestApplicationEngine.withAuthentication(
        userId: UUID = UUID.randomUUID(),
        login: String = "user@example.com",
        password: String = "pass",
        role: Pair<OrganizationRole, UUID>? = OrganizationRole.owner to UUID.randomUUID(),
        acl: Iterable<Triple<RoleType, Table, UUID>> = emptyList(),
        callback: JwtAuthenticationTrackingEngine.() -> Unit
    ): Unit = withAuthentication(
        userId,
        login,
        password,
        acl,
        roles = if (role !== null) arrayOf(role) else emptyArray(),
        callback
    )

    protected inline fun TestApplicationEngine.withAuthentication(
        userId: UUID = UUID.randomUUID(),
        login: String = "user@example.com",
        password: String = "pass",
        acl: Iterable<Triple<RoleType, Table, UUID>> = emptyList(),
        vararg roles: Pair<OrganizationRole, UUID> = arrayOf(OrganizationRole.owner to UUID.randomUUID()),
        callback: JwtAuthenticationTrackingEngine.() -> Unit
    ) {
        val accountService = declareMock<AccountService>()
        every { accountService.verifyUsersCredentials(login, password) } returns mockk {
            every { id } returns EntityID(userId, Users)
            every { email } returns login
        }
        if (roles.isNotEmpty()) {
            val rolesList: List<UserRoleInOrganization> = roles.map { role ->
                mockk {
                    every { user.id } returns EntityID(userId, Users)
                    every { organization.id } returns EntityID(role.second, Organizations)
                    every { this@mockk.role } returns transactionMain { role.first.toDB() }
                }
            }
            every { accountService.getRolesAssignedToUser(userId) } returns rolesList
        }
        val aclService = declareMock<ACLService>()
        val tableSlot = slot<Table>()
        val uuidSlot = slot<UUID>()
        val roleSlot = slot<RoleType>()
        val userIdSlot = slot<UUID>()
        every {
            aclService.checkAccess(capture(userIdSlot), capture(tableSlot), capture(uuidSlot), capture(roleSlot))
        } answers {
            if (userIdSlot.captured == userId) {
                for (ace in acl) {
                    if (ace.second == tableSlot.captured && ace.third == uuidSlot.captured) {
                        val allowedRoles = RoleType.values().filterTo(HashSet()) { it.ordinal >= ace.first.ordinal }
                        if (roleSlot.captured in allowedRoles)
                            return@answers
                    }
                }
            }
            throw ValidationException(ExceptionReason.InsufficientPermissionToURN, arrayOf("", ""))
        }
        every {
            aclService.getURN(Workspaces, capture(uuidSlot))
        } answers {
            //FIXME I tried to use callOriginal here, but couldn't make it work
            URN("urn:processm:db/${Workspaces.tableName}/${uuidSlot.captured}")
        }

        callback(JwtAuthenticationTrackingEngine(this, login, password))
    }

    protected class JwtAuthenticationTrackingEngine(
        private val engine: TestApplicationEngine, private val login: String, private val password: String
    ) {

        private val authenticationHeader: Pair<String, String>

        init {
            // Previously authentication was handled lazily, right before the request. It didn't work with coroutines, causing needless reauthentication. A similar situations happens for `by lazy`
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

        fun handleRequest(
            method: HttpMethod, uri: String, test: TestApplicationRequest.() -> Unit = {}
        ): TestApplicationCall {
            return engine.handleRequest(method, uri) {
                addHeader(authenticationHeader.first, authenticationHeader.second)
                test()
            }
        }

        fun handleWebSocketConversation(
            uri: String,
            callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
        ): TestApplicationCall {
            return engine.handleWebSocketConversation(uri, {
                addHeader(authenticationHeader.first, authenticationHeader.second)
            }, callback = callback)
        }

        // Based on https://youtrack.jetbrains.com/issue/KTOR-3290/Improve-support-for-testing-Server-Sent-Events-SSE
        suspend fun handleSse(
            uri: String,
            setup: TestApplicationRequest.() -> Unit = {},
            callback: suspend TestApplicationCall.(incoming: ByteReadChannel) -> Unit
        ): TestApplicationCall {
            val call = engine.createCall(closeRequest = false) {
                this.uri = uri
                addHeader(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                addHeader(authenticationHeader.first, authenticationHeader.second)
                setup()
                bodyChannel = ByteChannel(true)
            }

            engine.launch(call.coroutineContext) {
                // Execute server side.
                engine.pipeline.execute(call)
            }

            withContext(call.coroutineContext) {
                // responseChannelDeferred is internal, so we wait like this.
                // Ref: https://github.com/ktorio/ktor/blob/c5877a22c91fd693ea6dcd0b4e1924f05d3b6825/ktor-server/ktor-server-test-host/jvm/src/io/ktor/server/testing/TestApplicationEngine.kt#L225-L230
                var responseChannel: ByteReadChannel?
                do {
                    // Ensure status is absent or valid.
                    val status = call.response.status()
                    if (status?.isSuccess() == false) {
                        throw IllegalStateException(status.toString())
                    }

                    // Suspend, then try to grab response channel.
                    yield()
                    // websocketChannel is just responseChannel internally.
                    responseChannel = call.response.websocketChannel()
                } while (responseChannel == null)

                // Execute client side.
                call.callback(responseChannel)
            }

            return call
        }
    }

    protected inline fun <reified T> TestApplicationResponse.deserializeContent(): T =
        JsonSerializer.decodeFromString(requireNotNull(content))

    protected inline fun <reified T : Any> TestApplicationRequest.withSerializedBody(requestBody: T) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(JsonSerializer.encodeToString(requestBody))
    }

}
