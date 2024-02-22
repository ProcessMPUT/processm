package processm.services.api

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.test.mock.declareMock
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.services.JsonSerializer
import processm.services.api.models.*
import processm.services.api.models.Workspace
import processm.services.logic.Reason
import processm.services.logic.ValidationException
import processm.services.logic.WorkspaceService
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import java.util.stream.Stream
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {

    companion object {

        val artemis = Artemis()
        lateinit var pool: CloseableCoroutineDispatcher
        val Dispatchers.Request: CloseableCoroutineDispatcher
            get() = pool

        @JvmStatic
        @BeforeAll
        fun `start artemis`() {
            artemis.register()
            artemis.start()
            pool = Executors.newFixedThreadPool(7).asCoroutineDispatcher()
        }

        @JvmStatic
        @AfterAll
        fun `stop artemis`() {
            artemis.stop()
            pool.close()
        }
    }

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/workspaces",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    @Test
    fun `responds with 200 and workspace list`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val workspaceId1 = UUID.randomUUID()
        val workspaceId2 = UUID.randomUUID()

        withAuthentication(userId) {
            every { workspaceService.getUserWorkspaces(userId, organizationId) } returns listOf(
                mockk {
                    every { id } returns EntityID(workspaceId1, Workspaces)
                    every { name } returns "Workspace1"
                },
                mockk {
                    every { id } returns EntityID(workspaceId2, Workspaces)
                    every { name } returns "Workspace2"
                }
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/workspaces")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val workspaces = assertNotNull(response.deserializeContent<List<Workspace>>())
                assertEquals(2, workspaces.count())
                assertTrue { workspaces.any { it.id == workspaceId1 && it.name == "Workspace1" } }
                assertTrue { workspaces.any { it.id == workspaceId2 && it.name == "Workspace2" } }
            }
        }
    }

    @Test
    fun `responds to existing workspace removal request with 204`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            every {
                workspaceService.remove(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } just runs
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }

    @Test
    fun `responds to unknown workspace removal request with 404`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            every {
                workspaceService.remove(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } throws ValidationException(Reason.ResourceNotFound, "Workspace is not found")
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `responds to workspace removal request with insufficient permissions with 403 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/workspaces/${UUID.randomUUID()}"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user has insufficient permissions to access the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds to workspace creation request with no workspace name with 400 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.writer to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Workspace(""))
                }) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("Workspace name needs to be specified when creating new workspace")
                    )
                }
            }
        }

    @Test
    fun `responds to successful workspace creation request with 201 and workspace data`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val workspaceName = "Workspace1"

            withAuthentication(userId, role = OrganizationRole.writer to organizationId) {
                every { workspaceService.create(workspaceName, userId, organizationId) } returns workspaceId
                with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Workspace(workspaceName))
                }) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    assertEquals(workspaceId, response.deserializeContent<Workspace>().id)
                    assertEquals(workspaceName, response.deserializeContent<Workspace>().name)
                }
            }
        }

    @Test
    fun `responds to workspace creation request with insufficient permissions with 403 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(Workspace("Workspace1"))
                }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user has insufficient permissions to access the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds to workspace creation request with incorrect workspace data with 400 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces")) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The provided workspace data cannot be parsed")
                    )
                }
            }
        }

    @Test
    fun `responds to workspace components request with workspace not related to user with 403 and error message`() =
        withConfiguredTestApplication {
            withAuthentication {
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user is not a member of the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds to workspace components request with 200 and components list`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val dbSerializer = declareMock<DBSerializer>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val componentId1 = UUID.randomUUID()
        val componentId2 = UUID.randomUUID()
        val dataStore = UUID.randomUUID()

        val cnet1 = DBSerializer.insert(DBCache.get(dataStore.toString()).database, MutableCausalNet())
        val cnet2 = DBSerializer.insert(DBCache.get(dataStore.toString()).database, MutableCausalNet())

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every {
                workspaceService.getComponents(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } returns listOf(
                mockk(relaxed = true) {
                    every { id } returns EntityID(componentId1, WorkspaceComponents)
                    every { name } returns "Component1"
                    every { query } returns "query1"
                    every { dataStoreId } returns dataStore
                    every { componentType } returns ComponentTypeDto.CausalNet
                    every { data } returns cnet1.toString()
                    every { customizationData } returns null
                    every { layoutData } returns null
                    every { dataLastModified } returns null
                    every { userLastModified } returns Instant.now()
                    every { lastError } returns null
                },
                mockk(relaxed = true) {
                    every { id } returns EntityID(componentId2, WorkspaceComponents)
                    every { name } returns "Component2"
                    every { query } returns "query2"
                    every { dataStoreId } returns dataStore
                    every { componentType } returns ComponentTypeDto.CausalNet
                    every { data } returns cnet2.toString()
                    every { customizationData } returns null
                    every { layoutData } returns null
                    every { dataLastModified } returns null
                    every { userLastModified } returns Instant.now()
                    every { lastError } returns null
                }
            )
            every {
                dbSerializer.fetch(any(), any())
            } returns MutableCausalNet()
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/api/organizations/$organizationId/workspaces/$workspaceId/components"
                )
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                val components = assertNotNull(response.deserializeContent<List<AbstractComponent>>())
                assertEquals(2, components.count())
                assertEquals(componentId1, components[0].id)
                assertEquals(componentId2, components[1].id)
            }
        }
    }

    @Test
    fun `responds to workspace components request with 200 and component details`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val dbSerializer = declareMock<DBSerializer>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val componentId = UUID.randomUUID()
        val dataStore = UUID.randomUUID()

        val cnet1 = DBSerializer.insert(DBCache.get(dataStore.toString()).database, MutableCausalNet())

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every {
                workspaceService.getComponents(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } returns listOf(
                mockk {
                    every { id } returns EntityID(componentId, WorkspaceComponents)
                    every { name } returns "Component1"
                    every { query } returns "query1"
                    every { dataStoreId } returns dataStore
                    every { componentType } returns ComponentTypeDto.CausalNet
                    every { data } returns cnet1.toString()
                    every { customizationData } returns null
                    every { layoutData } returns "{\"x\":15,\"y\":30,\"width\":150,\"height\":300}"
                    every { dataLastModified } returns null
                    every { userLastModified } returns Instant.now()
                    every { lastError } returns null
                    every { algorithm } returns null
                }
            )
            every {
                dbSerializer.fetch(any(), any())
            } returns MutableCausalNet()
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/api/organizations/$organizationId/workspaces/$workspaceId/components"
                )
            ) {
                assertEquals(HttpStatusCode.OK, response.status())
                val layout = assertNotNull(response.deserializeContent<List<AbstractComponent>>().firstOrNull()?.layout)

                assertEquals(15.0, layout.x)
                assertEquals(30.0, layout.y)
                assertEquals(150.0, layout.width)
                assertEquals(300.0, layout.height)
            }
        }
    }

    @Test
    fun `responds to workspace component update without component customization data request with 204`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val componentId = UUID.randomUUID()
            val componentName = "Component1"
            val dataQuery = "query"
            val dataStore = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.addOrUpdateComponent(
                        componentId,
                        workspaceId,
                        any(),
                        organizationId,
                        componentName,
                        dataQuery,
                        dataStore,
                        ComponentTypeDto.CausalNet,
                        customizationData = null,
                        customProperties = emptyArray()
                    )
                } just Runs
                with(
                    handleRequest(
                        HttpMethod.Put,
                        "/api/organizations/$organizationId/workspaces/$workspaceId/components/$componentId"
                    ) {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        withSerializedBody(
                            AbstractComponent(
                                id = componentId,
                                query = dataQuery,
                                dataStore = dataStore,
                                name = "Component1",
                                type = ComponentType.causalNet,
                                customizationData = null,
                                customProperties = emptyArray()
                            )
                        )
                    }) {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                }
            }
        }

    @Test
    fun `responds to workspace component update request with 204`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val componentId = UUID.randomUUID()
        val componentName = "Component1"
        val dataQuery = "query"
        val dataStore = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every {
                workspaceService.addOrUpdateComponent(
                    componentId,
                    workspaceId,
                    any(),
                    organizationId,
                    componentName,
                    dataQuery,
                    dataStore,
                    ComponentTypeDto.CausalNet,
                    customizationData = """{"layout":[{"id":"id1","x":10.0,"y":10.0}]}""",
                    customProperties = any()
                )
            } just Runs
            with(
                handleRequest(
                    HttpMethod.Put,
                    "/api/organizations/$organizationId/workspaces/$workspaceId/components/$componentId"
                ) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(
                        AbstractComponent(
                            id = componentId,
                            query = dataQuery,
                            dataStore = dataStore,
                            name = "Component1",
                            type = ComponentType.causalNet,
                            customizationData = CustomizationData(
                                arrayOf(
                                    CustomizationDataLayoutInner(
                                        id = "id1",
                                        x = 10.0,
                                        y = 10.0
                                    )
                                )
                            ),
                            customProperties = emptyArray()
                        )
                    )
                }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }

    @Test
    fun `responds to workspace layout update request with 204`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val componentId = UUID.randomUUID()
            val layoutData = mapOf(
                componentId to LayoutElement(
                    1.0,
                    1.0,
                    2.0,
                    2.0
                )
            )

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.updateLayout(
                        workspaceId,
                        any(),
                        organizationId,
                        layoutData.mapValues { JsonSerializer.encodeToString(it.value) }
                    )
                } just Runs
                with(
                    handleRequest(
                        HttpMethod.Patch,
                        "/api/organizations/$organizationId/workspaces/$workspaceId/layout"
                    ) {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        withSerializedBody(LayoutCollectionMessageBody(layoutData.mapKeys { it.key.toString() }))
                    }) {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                }
            }
        }

    @Test
    fun `responds to workspace layout update request with unknown resource with 404 and error message`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val componentId = UUID.randomUUID()
            val layoutData = mapOf(
                componentId to LayoutElement(
                    1.0,
                    1.0,
                    2.0,
                    2.0
                )
            )

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.updateLayout(
                        workspaceId,
                        any(),
                        organizationId,
                        layoutData.mapValues { JsonSerializer.encodeToString(it.value) }
                    )
                } throws ValidationException(
                    Reason.ResourceNotFound,
                    "The specified workspace does not exist or the user has insufficient permissions to it"
                )
                with(
                    handleRequest(
                        HttpMethod.Patch,
                        "/api/organizations/$organizationId/workspaces/$workspaceId/layout"
                    ) {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        withSerializedBody(LayoutCollectionMessageBody(layoutData.mapKeys { it.key.toString() }))
                    }) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }

    @Test
    fun `responds to component removal request with 204`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val componentId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.removeComponent(
                        componentId,
                        workspaceId,
                        any(),
                        organizationId
                    )
                } just runs
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/workspaces/$workspaceId/components/$componentId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                }
            }
        }

    @Test
    fun `responds to component removal request with unknown resource with 404 and error message`() =
        withConfiguredTestApplication {
            val workspaceService = declareMock<WorkspaceService>()
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()
            val componentId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.removeComponent(
                        componentId,
                        workspaceId,
                        any(),
                        organizationId
                    )
                } throws ValidationException(
                    Reason.ResourceNotFound,
                    "The specified workspace/component does not exist or the user has insufficient permissions to it"
                )
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/workspaces/$workspaceId/components/$componentId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }

    data class SSE(val eventName: String?, val data: String)

    private fun SSE.asUpdateEvent(): UUID {
        assertEquals("update", eventName)
        return Json.decodeFromString<ComponentUpdateEventPayload>(data).componentId
    }

    /**
     * An unsound and incomplete parser of server-sent events
     *
     * @see https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation
     */
    private suspend fun ByteReadChannel.readSSE(): SSE {
        var eventName: String? = null
        val data = StringBuilder()
        while (true) {
            // This is sloppy, as readUTF8Line treats both \n and \r\n as line terminators, thus possibly leading to misinterpreting received data.
            // It doesn't seem to be a problem in the current use case and, nevertheless, it is recommended to encode the content of the event as JSON
            val line = readUTF8Line()
            if (line.isNullOrEmpty())
                break
            val i = line.indexOf(':')
            if (i <= 0)
                continue    // Ignore, even though the spec says something else
            val key = line.substring(0, i)
            var value = line.substring(i + 1)
            if (value[0] == ' ') value = value.substring(1)
            when (key) {
                "event" -> eventName = value
                "data" -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(value)
                }

                else -> error("Unknown field `$key'")
            }
        }
        return SSE(eventName, data.toString())
    }

    @Test
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `make 5 changes but receive only 2 of them and let the server handle broken connection`() {
        val result = ArrayList<UUID>()
        val componentId = UUID.randomUUID()
        withConfiguredTestApplication {
            val workspaceId = UUID.randomUUID()
            val component = mockk<WorkspaceComponent> {
                every { componentType } returns ComponentTypeDto.Kpi
                every { workspace } returns
                        mockk { every { id } returns EntityID(workspaceId, Workspaces) }
                every { id } returns EntityID(componentId, WorkspaceComponents)
            }
            val sync = Channel<Int>(Channel.UNLIMITED)
            withAuthentication {
                launch(context = Dispatchers.Request) {
                    sync.receive()
                    repeat(5) {
                        delay(200L)
                        println("Producing")
                        component.triggerEvent(Producer(), DATA_CHANGE)
                    }
                }
                runBlocking {
                    handleSse("/api/organizations/${UUID.randomUUID()}/workspaces/${workspaceId}") { channel ->
                        sync.send(1)
                        repeat(2) {
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
            }
        }
        assertEquals(2, result.size)
        assertEquals(componentId, result[0])
        assertEquals(componentId, result[1])

    }

    @Test
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `make changes to components in different workspaces one without subscription`() {
        val result = ArrayList<UUID>()
        val workspaceId1 = UUID.randomUUID()
        val workspaceId2 = UUID.randomUUID()
        val component1 = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId1, Workspaces) }
            every { id } returns EntityID(UUID.randomUUID(), WorkspaceComponents)
        }
        val component2 = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId2, Workspaces) }
            every { id } returns EntityID(UUID.randomUUID(), WorkspaceComponents)
        }
        withConfiguredTestApplication {
            val sync = Channel<Int>(Channel.UNLIMITED)
            withAuthentication {
                launch(context = Dispatchers.Request) {
                    sync.receive()
                    component1.triggerEvent(Producer(), DATA_CHANGE)
                    component2.triggerEvent(Producer(), DATA_CHANGE)
                }
                runBlocking {
                    handleSse("/api/organizations/${UUID.randomUUID()}/workspaces/${workspaceId2}") { channel ->
                        sync.send(1)
                        result.add(channel.readSSE().asUpdateEvent())
                    }
                }
            }
        }
        assertEquals(1, result.size)
        assertEquals(component2.id.value, result[0])
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(5, 50, 64, 128, 1024))
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `n subscriptions from a single client`(n: Int) {
        val result = ConcurrentLinkedDeque<UUID>()
        val workspaceId = UUID.randomUUID()
        val component = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId, Workspaces) }
            every { id } returns EntityID(UUID.randomUUID(), WorkspaceComponents)
        }
        withConfiguredTestApplication {
            val sync = Channel<Int>()
            withAuthentication {
                val jobs = (0 until n).map {
                    launch(context = Dispatchers.Request) {
                        handleSse("/api/organizations/${UUID.randomUUID()}/workspaces/${workspaceId}") { channel ->
                            sync.send(1)
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
                runBlocking {
                    repeat(n) { sync.receive() }
                    component.triggerEvent(Producer(), DATA_CHANGE)
                    jobs.forEach { it.join() }
                }
            }
        }
        assertEquals(n, result.size)
        assertTrue { result.all { it.equals(component.id.value) } }
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(5, 50, 64, 128, 1024))
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `n subscriptions from different clients`(n: Int) {
        val result = ConcurrentLinkedDeque<UUID>()
        val workspaceId = UUID.randomUUID()
        val component = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId, Workspaces) }
            every { id } returns EntityID(UUID.randomUUID(), WorkspaceComponents)
        }
        withConfiguredTestApplication {
            val sync = Channel<Int>(Channel.UNLIMITED)
            val jobs = (0 until n).map { ctr ->
                launch(context = Dispatchers.Request) {
                    withAuthentication(login = "user${ctr}@example.com") {
                        handleSse("/api/organizations/${UUID.randomUUID()}/workspaces/${workspaceId}") { channel ->
                            sync.send(ctr)
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
            }
            runBlocking {
                repeat(n) { sync.receive() }
                component.triggerEvent(Producer(), DATA_CHANGE)
                jobs.forEach { it.join() }
            }
        }
        assertEquals(n, result.size)
        assertTrue { result.all { it.equals(component.id.value) } }
    }
}
