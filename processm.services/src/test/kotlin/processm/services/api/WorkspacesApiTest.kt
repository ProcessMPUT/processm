package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.services.api.models.WorkspaceCollectionMessageBody
import processm.services.logic.WorkspaceService
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
}
