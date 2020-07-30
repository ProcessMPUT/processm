package processm.services.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.services.api.models.ErrorMessageBody
import processm.services.api.models.GroupCollectionMessageBody
import processm.services.api.models.OrganizationMemberCollectionMessageBody
import processm.services.api.models.OrganizationRole
import processm.services.logic.OrganizationService
import processm.services.logic.ValidationException
import processm.services.models.OrganizationRoleDto
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}"
    )

    override fun componentsRegistration() {
        super.componentsRegistration()
        organizationService = declareMock()
    }

    lateinit var organizationService: OrganizationService

    @Test
    fun `responds to organization groups request with 200 and groups list`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val groupId1 = UUID.randomUUID()
        val groupId2 = UUID.randomUUID()

        withAuthentication(userId) {
            every { organizationService.getOrganizationGroups(organizationId) } returns listOf(
                mockk {
                    every { id } returns groupId1
                    every { name } returns "Group1"
                    every { isImplicit } returns true
                    every { organization.id } returns organizationId
                },
                mockk {
                    every { id } returns groupId2
                    every { name } returns "Group2"
                    every { isImplicit } returns false
                    every { organization.id } returns organizationId
                }
            )
            every { accountService.getRolesAssignedToUser(userId) } returns listOf(
                mockk {
                    every { user.id } returns userId
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val groups = assertNotNull(response.deserializeContent<GroupCollectionMessageBody>().data)

                assertEquals(2, groups.count())
                assertTrue { groups.any { it.id == groupId1 && it.name == "Group1" && it.isImplicit } }
                assertTrue { groups.any { it.id == groupId2 && it.name == "Group2" && !it.isImplicit } }
            }
        }
    }

    @Test
    fun `responds to random organization groups list request with 403 and error message`() = withConfiguredTestApplication {
        val unknownOrganizationId = UUID.randomUUID()

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$unknownOrganizationId/groups")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user is not a member of the organization with the provided id"))
            }
        }
    }

    @Test
    fun `responds to groups list of recently removed organization request with 404 and error message`() = withConfiguredTestApplication {
        val removedOrganizationId = UUID.randomUUID()

        withAuthentication {
            every { organizationService.getOrganizationGroups(removedOrganizationId) } throws ValidationException(ValidationException.Reason.ResourceNotFound, userMessage = "Organization not found")
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns removedOrganizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/organizations/$removedOrganizationId/groups")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("Organization not found"))
            }
        }
    }

    @Test
    fun `responds to organization members request with 200 and members list`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberId1 = UUID.randomUUID()
        val memberId2 = UUID.randomUUID()

        withAuthentication(userId) {
            every { organizationService.getOrganizationMembers(organizationId) } returns listOf(
                mockk {
                    every { user.id } returns memberId1
                    every { user.email } returns "user1@example.com"
                    every { role } returns OrganizationRoleDto.Reader
                },
                mockk {
                    every { user.id } returns memberId2
                    every { user.email } returns "user2@example.com"
                    every { role } returns OrganizationRoleDto.Writer
                }
            )
            every { accountService.getRolesAssignedToUser(userId) } returns listOf(
                mockk {
                    every { user.id } returns userId
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/members")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val members = assertNotNull(response.deserializeContent<OrganizationMemberCollectionMessageBody>().data)

                assertEquals(2, members.count())
                assertTrue { members.any { it.id == memberId1 && it.username == "user1@example.com" && it.organizationRole == OrganizationRole.reader } }
                assertTrue { members.any { it.id == memberId2 && it.username == "user2@example.com" && it.organizationRole == OrganizationRole.writer } }
            }
        }
    }

    @Test
    fun `responds to random organization members list request with 403 and error message`() = withConfiguredTestApplication {
        val unknownOrganizationId = UUID.randomUUID()

        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$unknownOrganizationId/members")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user is not a member of the organization with the provided id"))
            }
        }
    }

    @Test
    fun `responds to members list of recently removed organization request with 404 and error message`() = withConfiguredTestApplication {
        val removedOrganizationId = UUID.randomUUID()

        withAuthentication {
            every { organizationService.getOrganizationMembers(removedOrganizationId) } throws ApiException(publicMessage = "", responseCode = HttpStatusCode.NotFound)
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns removedOrganizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/organizations/$removedOrganizationId/members")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

}
