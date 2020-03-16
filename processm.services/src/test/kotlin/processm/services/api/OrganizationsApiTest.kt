package processm.services.api

import io.ktor.http.HttpMethod
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        Pair(HttpMethod.Get, "/api/organizations"),
        Pair(HttpMethod.Post, "/api/organizations"),
        Pair(HttpMethod.Get, "/api/organizations/1"),
        Pair(HttpMethod.Put, "/api/organizations/1"),
        Pair(HttpMethod.Delete, "/api/organizations/1"),
        Pair(HttpMethod.Get, "/api/organizations/1/members"),
        Pair(HttpMethod.Post, "/api/organizations/1/members"),
        Pair(HttpMethod.Delete, "/api/organizations/1/members/1")
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        Pair(HttpMethod.Get, "/api/organizations"),
        Pair(HttpMethod.Post, "/api/organizations"),
        Pair(HttpMethod.Get, "/api/organizations/1"),
        Pair(HttpMethod.Put, "/api/organizations/1"),
        Pair(HttpMethod.Delete, "/api/organizations/1"),
        Pair(HttpMethod.Get, "/api/organizations/1/members"),
        Pair(HttpMethod.Post, "/api/organizations/1/members"),
        Pair(HttpMethod.Delete, "/api/organizations/1/members/1")
    )

}
