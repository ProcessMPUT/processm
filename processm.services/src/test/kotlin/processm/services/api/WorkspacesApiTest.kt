package processm.services.api

import io.ktor.http.HttpMethod
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacesApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        Pair(HttpMethod.Get, "/api/workspaces"),
        Pair(HttpMethod.Post, "/api/workspaces"),
        Pair(HttpMethod.Get, "/api/workspaces/1"),
        Pair(HttpMethod.Put, "/api/workspaces/1"),
        Pair(HttpMethod.Delete, "/api/workspaces/1")
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        Pair(HttpMethod.Get, "/api/workspaces"),
        Pair(HttpMethod.Post, "/api/workspaces"),
        Pair(HttpMethod.Get, "/api/workspaces/1"),
        Pair(HttpMethod.Put, "/api/workspaces/1"),
        Pair(HttpMethod.Delete, "/api/workspaces/1")
    )

}
