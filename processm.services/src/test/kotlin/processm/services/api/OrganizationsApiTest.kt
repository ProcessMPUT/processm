package processm.services.api

import io.ktor.http.HttpMethod
import org.junit.jupiter.api.TestInstance
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/organizations",
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Get to "/api/organizations/1",
        HttpMethod.Put to "/api/organizations/1",
        HttpMethod.Delete to "/api/organizations/1",
        HttpMethod.Get to "/api/organizations/1/members",
        HttpMethod.Post to "/api/organizations/1/members",
        HttpMethod.Delete to "/api/organizations/1/members/1"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Get to "/api/organizations",
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Get to "/api/organizations/1",
        HttpMethod.Put to "/api/organizations/1",
        HttpMethod.Delete to "/api/organizations/1",
        HttpMethod.Get to "/api/organizations/1/members",
        HttpMethod.Post to "/api/organizations/1/members",
        HttpMethod.Delete to "/api/organizations/1/members/1"
    )

}
