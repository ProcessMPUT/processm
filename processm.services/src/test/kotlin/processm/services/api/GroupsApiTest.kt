package processm.services.api

import io.ktor.http.HttpMethod
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        Pair(HttpMethod.Get, "/api/groups"),
        Pair(HttpMethod.Post, "/api/groups"),
        Pair(HttpMethod.Get, "/api/groups/1"),
        Pair(HttpMethod.Put, "/api/groups/1"),
        Pair(HttpMethod.Delete, "/api/groups/1"),
        Pair(HttpMethod.Get, "/api/groups/1/members"),
        Pair(HttpMethod.Post, "/api/groups/1/members"),
        Pair(HttpMethod.Delete, "/api/groups/1/members/1"),
        Pair(HttpMethod.Get, "/api/groups/1/subgroups"),
        Pair(HttpMethod.Post, "/api/groups/1/subgroups"),
        Pair(HttpMethod.Delete, "/api/groups/1/subgroups/1")
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        Pair(HttpMethod.Get, "/api/groups"),
        Pair(HttpMethod.Post, "/api/groups"),
        Pair(HttpMethod.Get, "/api/groups/1"),
        Pair(HttpMethod.Put, "/api/groups/1"),
        Pair(HttpMethod.Delete, "/api/groups/1"),
        Pair(HttpMethod.Get, "/api/groups/1/members"),
        Pair(HttpMethod.Post, "/api/groups/1/members"),
        Pair(HttpMethod.Delete, "/api/groups/1/members/1"),
        Pair(HttpMethod.Get, "/api/groups/1/subgroups"),
        Pair(HttpMethod.Post, "/api/groups/1/subgroups"),
        Pair(HttpMethod.Delete, "/api/groups/1/subgroups/1")
    )

}
