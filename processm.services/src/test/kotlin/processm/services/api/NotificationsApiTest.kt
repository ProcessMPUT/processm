package processm.services.api

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.test.mock.declareMock
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.models.metadata.URN
import processm.dbmodels.models.*
import processm.services.helpers.asUpdateEvent
import processm.services.helpers.readSSE
import processm.services.logic.ACLService
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.*

class NotificationsApiTest : BaseApiTest() {

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
        HttpMethod.Get to "/api/notifications",
    )


    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/notifications"
    )

    private lateinit var workspaceId1: UUID
    private lateinit var workspaceId2: UUID
    private lateinit var componentId1: UUID
    private lateinit var componentId2: UUID
    private lateinit var component1: WorkspaceComponent
    private lateinit var component2: WorkspaceComponent

    @BeforeTest
    fun `mock components and workspaces`() {
        workspaceId1 = UUID.randomUUID()
        workspaceId2 = UUID.randomUUID()
        componentId1 = UUID.randomUUID()
        componentId2 = UUID.randomUUID()

        component1 = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId1, Workspaces) }
            every { id } returns EntityID(componentId1, WorkspaceComponents)
            every { name } returns "Component 1"
        }
        component2 = mockk<WorkspaceComponent> {
            every { componentType } returns ComponentTypeDto.Kpi
            every { workspace } returns
                    mockk { every { id } returns EntityID(workspaceId2, Workspaces) }
            every { id } returns EntityID(componentId2, WorkspaceComponents)
            every { name } returns "Component 2"
        }
        mockkObject(Workspace)
        every { Workspace[workspaceId1] } returns mockk {
            every { name } returns "Workspace 1"
        }
        every { Workspace[workspaceId2] } returns mockk {
            every { name } returns "Workspace 2"
        }
        mockkObject(WorkspaceComponent)
        every { WorkspaceComponent[componentId1] } returns component1
        every { WorkspaceComponent[componentId2] } returns component2
    }

    @AfterTest
    fun unmock() {
        unmockkAll()
    }

    @Test
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `make 5 changes but receive only 2 of them and let the server handle broken connection`() {
        val result = ArrayList<UUID>()
        withConfiguredTestApplication {
            val userId = UUID.randomUUID()
            with(declareMock<ACLService>()) {
                every {
                    usersWithAccess(URN("urn:processm:db/workspaces/$workspaceId1"), any(), any())
                } returns listOf(userId)
            }
            val sync = Channel<Int>(Channel.UNLIMITED)
            withAuthentication(userId) {
                launch(context = Dispatchers.Request) {
                    sync.receive()
                    repeat(5) {
                        delay(200L)
                        println("Producing")
                        component1.triggerEvent(Producer(), WorkspaceComponentEventType.DataChange)
                    }
                }
                runBlocking {
                    handleSse("/api/notifications") { channel ->
                        sync.send(1)
                        repeat(2) {
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
            }
        }
        assertEquals(2, result.size)
        assertEquals(componentId1, result[0])
        assertEquals(componentId1, result[1])

    }

    @Test
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `make changes to components in different workspaces one without subscription`() {
        val result = ArrayList<UUID>()
        val userId = UUID.randomUUID()

        withConfiguredTestApplication {
            with(declareMock<ACLService>()) {
                every {
                    usersWithAccess(URN("urn:processm:db/workspaces/$workspaceId1"), any(), any())
                } returns emptyList()
                every {
                    usersWithAccess(URN("urn:processm:db/workspaces/$workspaceId2"), any(), any())
                } returns listOf(userId)
            }
            val sync = Channel<Int>(Channel.UNLIMITED)
            withAuthentication(userId) {
                launch(context = Dispatchers.Request) {
                    sync.receive()
                    component1.triggerEvent(Producer(), WorkspaceComponentEventType.DataChange)
                    component2.triggerEvent(Producer(), WorkspaceComponentEventType.DataChange)
                }
                runBlocking {
                    handleSse("/api/notifications") { channel ->
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
        val userId = UUID.randomUUID()
        withConfiguredTestApplication {
            val sync = Channel<Int>()
            withAuthentication(userId) {
                with(declareMock<ACLService>()) {
                    every {
                        usersWithAccess(URN("urn:processm:db/workspaces/$workspaceId1"), any(), any())
                    } returns listOf(userId)
                }
                val jobs = (0 until n).map {
                    launch(context = Dispatchers.Request) {
                        handleSse("/api/notifications") { channel ->
                            sync.send(1)
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
                runBlocking {
                    repeat(n) { sync.receive() }
                    component1.triggerEvent(Producer(), WorkspaceComponentEventType.DataChange)
                    jobs.forEach { it.join() }
                }
            }
        }
        assertEquals(n, result.size)
        assertTrue { result.all { it.equals(component1.id.value) } }
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(5, 50, 64, 128, 1024))
    @Timeout(10L, unit = TimeUnit.SECONDS)
    fun `n subscriptions from different clients`(n: Int) {
        val result = ConcurrentLinkedDeque<UUID>()
        val userIds = List(n) { UUID.randomUUID() }
        withConfiguredTestApplication {
            with(declareMock<ACLService>()) {
                every {
                    usersWithAccess(URN("urn:processm:db/workspaces/$workspaceId1"), any(), any())
                } returns userIds
            }
            val sync = Channel<Int>(Channel.UNLIMITED)
            val jobs = (0 until n).map { ctr ->
                launch(context = Dispatchers.Request) {
                    withAuthentication(userIds[ctr], login = "user${ctr}@example.com") {
                        handleSse("/api/notifications") { channel ->
                            sync.send(ctr)
                            result.add(channel.readSSE().asUpdateEvent())
                        }
                    }
                }
            }
            runBlocking {
                repeat(n) { sync.receive() }
                component1.triggerEvent(Producer(), WorkspaceComponentEventType.DataChange)
                jobs.forEach { it.join() }
            }
        }
        assertEquals(n, result.size)
        assertTrue { result.all { it.equals(componentId1) } }
    }
}