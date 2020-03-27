package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.WorkspaceCollectionMessageBody
import processm.services.api.models.WorkspaceMessageBody
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/workspaces",
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Get to "/api/workspaces/1",
        HttpMethod.Put to "/api/workspaces/1",
        HttpMethod.Delete to "/api/workspaces/1"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Put to "/api/workspaces/1",
        HttpMethod.Delete to "/api/workspaces/1"
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
            with(handleRequest(HttpMethod.Get, "/api/workspaces/2")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("2", response.deserializeContent<WorkspaceMessageBody>().data.id)
            }
        }
    }

}
