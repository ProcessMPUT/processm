package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import processm.services.api.models.GroupCollectionMessageBody
import processm.services.api.models.GroupMessageBody
import processm.services.api.models.GroupRole
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/groups",
        HttpMethod.Post to "/api/groups",
        HttpMethod.Get to "/api/groups/1",
        HttpMethod.Put to "/api/groups/1",
        HttpMethod.Delete to "/api/groups/1",
        HttpMethod.Get to "/api/groups/1/members",
        HttpMethod.Post to "/api/groups/1/members",
        HttpMethod.Delete to "/api/groups/1/members/1",
        HttpMethod.Get to "/api/groups/1/subgroups",
        HttpMethod.Post to "/api/groups/1/subgroups",
        HttpMethod.Delete to "/api/groups/1/subgroups/1"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/groups",
        HttpMethod.Put to "/api/groups/1",
        HttpMethod.Delete to "/api/groups/1",
        HttpMethod.Get to "/api/groups/1/members",
        HttpMethod.Post to "/api/groups/1/members",
        HttpMethod.Delete to "/api/groups/1/members/1",
        HttpMethod.Get to "/api/groups/1/subgroups",
        HttpMethod.Post to "/api/groups/1/subgroups",
        HttpMethod.Delete to "/api/groups/1/subgroups/1"
    )

    @Test
    fun `responds with 200 and group list`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/groups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.deserializeContent<GroupCollectionMessageBody>().data)
            }
        }
    }

    @Test
    fun `responds with 200 and specified workspace`() = withConfiguredTestApplication {
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/groups/2")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val responseContent = response.deserializeContent<GroupMessageBody>()
                assertEquals("2", responseContent.data.id)
                assertEquals(GroupRole.owner, responseContent.data.groupRole)
            }
        }
    }

}
