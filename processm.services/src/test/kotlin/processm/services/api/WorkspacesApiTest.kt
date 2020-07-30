package processm.services.api

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.services.api.models.*
import processm.services.logic.WorkspaceService
import processm.services.models.CausalNetDto
import processm.services.models.CausalNetEdgeDto
import processm.services.models.CausalNetNodeDto
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/workspaces",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components/${UUID.randomUUID()}/data"
    )

    override fun componentsRegistration() {
        super.componentsRegistration()
        workspaceService = declareMock()
    }

    lateinit var workspaceService: WorkspaceService

    @Test
    fun `responds with 200 and workspace list`() = withConfiguredTestApplication {
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
                val workspaces = assertNotNull(response.deserializeContent<WorkspaceCollectionMessageBody>().data)
                assertEquals(2, workspaces.count())
                assertTrue { workspaces.any { it.id == workspaceId1 && it.name == "Workspace1" } }
                assertTrue { workspaces.any { it.id == workspaceId2 && it.name == "Workspace2" } }
            }
        }
    }

    @Test
    fun `responds to existing workspace removal request with 204`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            every { workspaceService.removeWorkspace(workspaceId, userId = any(), organizationId = organizationId) } returns true
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }

    @Test
    fun `responds to unknown workspace removal request with 404`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            every { workspaceService.removeWorkspace(workspaceId, userId = any(), organizationId = organizationId) } returns false
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `responds to workspace removal request with insufficient permissions with 403 and error message`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/workspaces/${UUID.randomUUID()}")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user has insufficient permissions to access the related organization"))
            }
        }
    }

    @Test
    fun `responds to workspace creation request with no workspace name with 400 and error message`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(WorkspaceMessageBody(Workspace("")))
            }) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("Workspace name needs to be specified when creating new workspace"))
            }
        }
    }

    @Test
    fun `responds to successful workspace creation request with 201 and workspace data`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val workspaceName = "Workspace1"

        withAuthentication(userId, role = OrganizationRole.writer to organizationId) {
            every { workspaceService.createWorkspace(workspaceName, userId, organizationId) } returns workspaceId
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(WorkspaceMessageBody(Workspace(workspaceName)))
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
                assertEquals(workspaceId, response.deserializeContent<WorkspaceMessageBody>().data.id)
                assertEquals(workspaceName, response.deserializeContent<WorkspaceMessageBody>().data.name)
            }
        }
    }

    @Test
    fun `responds to workspace creation request with insufficient permissions with 403 and error message`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                withSerializedBody(WorkspaceMessageBody(Workspace("Workspace1")))
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user has insufficient permissions to access the related organization"))
            }
        }
    }

    @Test
    fun `responds to workspace creation request with incorrect workspace data with 400 and error message`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/workspaces")) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The provided workspace data cannot be parsed"))
            }
        }
    }

    @Test
    fun `responds to workspace components request with workspace not related to user with 403 and error message`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/organizations/${UUID.randomUUID()}/workspaces/${UUID.randomUUID()}/components")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user is not a member of the related organization"))
            }
        }
    }

    @Test
    fun `responds to workspace components request with 200 and components list`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        val componentId1 = UUID.randomUUID()
        val componentId2 = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every { workspaceService.getWorkspaceComponents(workspaceId) } returns listOf(
                mockk {
                    every { id } returns componentId1
                    every { name } returns "Component1"
                    every { data } returns CausalNetDto(
                        listOf(CausalNetNodeDto("node_id", arrayOf(arrayOf()), arrayOf(arrayOf()))),
                        listOf(CausalNetEdgeDto("node_id1", "node_id2")))
                },
                mockk {
                    every { id } returns componentId2
                    every { name } returns "Component2"
                    every { data } returns CausalNetDto(
                        listOf(CausalNetNodeDto("node_id", arrayOf(arrayOf()), arrayOf(arrayOf()))),
                        listOf(CausalNetEdgeDto("node_id1", "node_id2")))
                }
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/workspaces/$workspaceId/components")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val components = assertNotNull(response.deserializeContent<ComponentCollectionMessageBody>().data)
                assertEquals(2, components.count())
                assertEquals(componentId1, components[0].id)
                assertEquals(componentId2, components[1].id)
            }
        }
    }
}
