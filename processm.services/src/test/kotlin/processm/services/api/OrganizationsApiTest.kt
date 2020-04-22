package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.GroupCollectionMessageBody
import processm.services.api.models.OrganizationMessageBody
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/organizations",
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/members",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/members",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}"
    )

    @Test
    fun `responds with 200 and group list`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/organizations")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<GroupCollectionMessageBody>().data)
            }
        }
    }

    @Test
    fun `responds with 200 and specified workspace`() = withConfiguredTestApplication {
        withAuthentication {
            val organizationId = UUID.randomUUID()
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(organizationId, response.deserializeContent<OrganizationMessageBody>().data.id)
            }
        }
    }

}
