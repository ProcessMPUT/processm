package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.WorkspaceCollectionMessageBody
import processm.services.api.models.WorkspaceMessageBody
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/workspaces",
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Get to "/api/workspaces/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/workspaces/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/workspaces/${UUID.randomUUID()}"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Put to "/api/workspaces/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/workspaces/${UUID.randomUUID()}"
    )

    @Test
    fun `responds with 200 and workspace list`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/workspaces")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<WorkspaceCollectionMessageBody>().data)
            }
        }
    }

    @Test
    fun `responds with 200 and specified workspace`() = withConfiguredTestApplication {
        withAuthentication {
            val workspaceId = UUID.randomUUID()
            with(handleRequest(HttpMethod.Get, "/api/workspaces/$workspaceId")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(workspaceId, response.deserializeContent<WorkspaceMessageBody>().data.id)
            }
        }
    }

}
