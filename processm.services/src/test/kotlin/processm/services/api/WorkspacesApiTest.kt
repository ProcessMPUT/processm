package processm.services.api

import com.google.gson.Gson
import io.ktor.http.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponents
import processm.services.api.models.*
import processm.services.logic.Reason
import processm.services.logic.ValidationException
import processm.services.logic.WorkspaceService
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {
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
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
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
                    every { id } returns workspaceId1
                    every { name } returns "Workspace1"
                },
                mockk {
                    every { id } returns workspaceId2
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
                workspaceService.removeWorkspace(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } returns true
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
                workspaceService.removeWorkspace(
                    workspaceId,
                    userId = any(),
                    organizationId = organizationId
                )
            } returns false
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
                every { workspaceService.createWorkspace(workspaceName, userId, organizationId) } returns workspaceId
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
                workspaceService.getWorkspaceComponents(
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

    @Ignore("See #148")
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
                workspaceService.getWorkspaceComponents(
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
                    every { customizationData } returns "{\"layout\":[{\"id\":\"node_id\",\"x\":15,\"y\":30}]}"
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
                TODO("Fix the following commented-out lines so that they compile and the test passes")
//                val componentCustomizationData =
//                    assertNotNull(response.deserializeContent<ComponentCollectionMessageBody>().data.firstOrNull()?.customizationData?.layout)
//
//                assertEquals("node_id", componentCustomizationData.firstOrNull()?.id)
//                assertEquals(15.toBigDecimal(), componentCustomizationData.firstOrNull()?.x)
//                assertEquals(30.toBigDecimal(), componentCustomizationData.firstOrNull()?.y)
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
                    workspaceService.addOrUpdateWorkspaceComponent(
                        componentId,
                        workspaceId,
                        any(),
                        organizationId,
                        componentName,
                        dataQuery,
                        dataStore,
                        ComponentTypeDto.CausalNet,
                        customizationData = null
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
                                customizationData = null
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
                workspaceService.addOrUpdateWorkspaceComponent(
                    componentId,
                    workspaceId,
                    any(),
                    organizationId,
                    componentName,
                    dataQuery,
                    dataStore,
                    ComponentTypeDto.CausalNet,
                    customizationData = """{"layout":[{"id":"id1","x":10.0,"y":10.0}]}"""
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
                            customizationData = CausalNetComponentAllOfCustomizationData(
                                arrayOf(
                                    CausalNetComponentAllOfCustomizationDataLayout(
                                        id = "id1",
                                        x = 10.toBigDecimal(),
                                        y = 10.toBigDecimal()
                                    )
                                )
                            )
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
                    1.toBigDecimal(),
                    1.toBigDecimal(),
                    2.toBigDecimal(),
                    2.toBigDecimal()
                )
            )

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.updateWorkspaceLayout(
                        workspaceId,
                        any(),
                        organizationId,
                        layoutData.mapValues { Gson().toJson(it.value) }
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
                    1.toBigDecimal(),
                    1.toBigDecimal(),
                    2.toBigDecimal(),
                    2.toBigDecimal()
                )
            )

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                every {
                    workspaceService.updateWorkspaceLayout(
                        workspaceId,
                        any(),
                        organizationId,
                        layoutData.mapValues { Gson().toJson(it.value) }
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
                    workspaceService.removeWorkspaceComponent(
                        componentId,
                        workspaceId,
                        any(),
                        organizationId
                    )
                } returns true
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
                    workspaceService.removeWorkspaceComponent(
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
}
