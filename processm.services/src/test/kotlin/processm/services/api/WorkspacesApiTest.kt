package processm.services.api

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.core.esb.Artemis
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.RoleType
import processm.dbmodels.models.WorkspaceComponents
import processm.dbmodels.models.Workspaces
import processm.services.JsonSerializer
import processm.services.api.models.*
import processm.services.helpers.ExceptionReason
import processm.services.logic.ValidationException
import processm.services.logic.WorkspaceService
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        HttpMethod.Get to "/api/workspaces",
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Put to "/api/workspaces/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/workspaces/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/workspaces/${UUID.randomUUID()}/components",
        HttpMethod.Get to "/api/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Delete to "/api/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    @Test
    fun `responds with 200 and workspace list`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val workspaceId1 = UUID.randomUUID()
        val workspaceId2 = UUID.randomUUID()

        withAuthentication(userId) {
            every { workspaceService.getUserWorkspaces(userId) } returns listOf(
                mockk<processm.dbmodels.models.Workspace> {
                    every { id } returns EntityID(workspaceId1, Workspaces)
                    every { name } returns "Workspace1"
                } to RoleType.Owner,
                mockk<processm.dbmodels.models.Workspace> {
                    every { id } returns EntityID(workspaceId2, Workspaces)
                    every { name } returns "Workspace2"
                } to RoleType.Writer
            )
            with(handleRequest(HttpMethod.Get, "/api/workspaces")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val workspaces = assertNotNull(response.deserializeContent<List<Workspace>>())
                assertEquals(2, workspaces.count())
                assertTrue { workspaces.any { it.id == workspaceId1 && it.name == "Workspace1" && it.role == OrganizationRole.owner } }
                assertTrue { workspaces.any { it.id == workspaceId2 && it.name == "Workspace2" && it.role == OrganizationRole.writer } }
            }
        }
    }

    @Test
    fun `responds to existing workspace removal request with 204`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(acl = acl { RoleType.Owner * Workspaces * workspaceId }) {
            every {
                workspaceService.remove(workspaceId)
            } just runs
            with(handleRequest(HttpMethod.Delete, "/api/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }

    @Test
    fun `responds to unknown workspace removal request with 404`() = withConfiguredTestApplication {
        val workspaceService = declareMock<WorkspaceService>()
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(acl = acl { RoleType.Owner * Workspaces * workspaceId }) {
            every {
                workspaceService.remove(workspaceId)
            } throws ValidationException(ExceptionReason.WorkspaceNotFound)
            with(handleRequest(HttpMethod.Delete, "/api/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `responds to workspace removal request with insufficient permissions with 403`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val workspaceId = UUID.randomUUID()

            withAuthentication(acl = acl { RoleType.Reader * Workspaces * workspaceId }) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/workspaces/$workspaceId"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            }
        }

    @Test
    fun `responds to workspace creation request with no workspace name with 400 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.writer to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(NewWorkspace("", organizationId))
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
                with(handleRequest(HttpMethod.Post, "/api/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(NewWorkspace(workspaceName, organizationId))
                }) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    assertEquals(workspaceId, response.deserializeContent<Workspace>().id)
                    assertEquals(workspaceName, response.deserializeContent<Workspace>().name)
                }
            }
        }

    @Test
    fun `responds to workspace creation request with insufficient permissions with 403`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication() {
                with(handleRequest(HttpMethod.Post, "/api/workspaces") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    withSerializedBody(NewWorkspace("Workspace1", organizationId))
                }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            }
        }

    @Test
    fun `responds to workspace creation request with incorrect workspace data with 400 and error message`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/workspaces")) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("data cannot be parsed")
                    )
                }
            }
        }

    @Test
    fun `responds to workspace components request with workspace not related to user with 403`() =
        withConfiguredTestApplication {
            withAuthentication {
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/api/workspaces/${UUID.randomUUID()}/components"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
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

        withAuthentication(acl = acl { RoleType.Reader * Workspaces * workspaceId }) {
            every {
                workspaceService.getComponents(
                    workspaceId
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
                    "/api/workspaces/$workspaceId/components"
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

        withAuthentication(acl = acl { RoleType.Reader * Workspaces * workspaceId }) {
            every {
                workspaceService.getComponents(
                    workspaceId
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
                    every { properties } returns emptyMap()
                }
            )
            every {
                dbSerializer.fetch(any(), any())
            } returns MutableCausalNet()
            with(
                handleRequest(
                    HttpMethod.Get,
                    "/api/workspaces/$workspaceId/components"
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

            withAuthentication(acl = acl { RoleType.Writer * Workspaces * workspaceId }) {
                every {
                    workspaceService.addOrUpdateComponent(
                        componentId,
                        workspaceId,
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
                        "/api/workspaces/$workspaceId/components/$componentId"
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

        withAuthentication(acl = acl { RoleType.Writer * Workspaces * workspaceId }) {
            every {
                workspaceService.addOrUpdateComponent(
                    componentId,
                    workspaceId,
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
                    "/api/workspaces/$workspaceId/components/$componentId"
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

            withAuthentication(acl = acl { RoleType.Writer * Workspaces * workspaceId }) {
                every {
                    workspaceService.updateLayout(
                        layoutData.mapValues { JsonSerializer.encodeToString(it.value) }
                    )
                } just Runs
                with(
                    handleRequest(
                        HttpMethod.Patch,
                        "/api/workspaces/$workspaceId/layout"
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

            withAuthentication(acl = acl { RoleType.Reader * Workspaces * workspaceId }) {
                every {
                    workspaceService.updateLayout(
                        layoutData.mapValues { JsonSerializer.encodeToString(it.value) }
                    )
                } throws ValidationException(ExceptionReason.WorkspaceNotFound)
                with(
                    handleRequest(
                        HttpMethod.Patch,
                        "/api/workspaces/$workspaceId/layout"
                    ) {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        withSerializedBody(LayoutCollectionMessageBody(layoutData.mapKeys { it.key.toString() }))
                    }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
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

            withAuthentication(acl = acl { RoleType.Owner * Workspaces * workspaceId }) {
                every {
                    workspaceService.removeComponent(
                        componentId
                    )
                } just runs
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/workspaces/$workspaceId/components/$componentId"
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

            withAuthentication(acl = acl { RoleType.Owner * Workspaces * workspaceId }) {
                every {
                    workspaceService.removeComponent(componentId)
                } throws ValidationException(ExceptionReason.WorkspaceComponentNotFound)
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/workspaces/$workspaceId/components/$componentId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }
}
