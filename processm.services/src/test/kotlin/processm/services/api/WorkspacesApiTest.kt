package processm.services.api

import io.ktor.http.HttpMethod
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

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
        HttpMethod.Get to "/api/workspaces",
        HttpMethod.Post to "/api/workspaces",
        HttpMethod.Get to "/api/workspaces/1",
        HttpMethod.Put to "/api/workspaces/1",
        HttpMethod.Delete to "/api/workspaces/1"
    )

}
