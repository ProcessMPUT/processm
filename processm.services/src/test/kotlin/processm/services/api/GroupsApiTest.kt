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
import processm.services.api.models.GroupMessageBody
import processm.services.api.models.GroupRole
import processm.services.logic.GroupService
import processm.services.logic.ValidationException
import processm.services.models.OrganizationRoleDto
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupsApiTest : BaseApiTest() {

    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/groups",
        HttpMethod.Post to "/api/groups",
        HttpMethod.Get to "/api/groups/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/groups/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/groups/${UUID.randomUUID()}/members",
        HttpMethod.Post to "/api/groups/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Post to "/api/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}/subgroups/${UUID.randomUUID()}"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/groups",
        HttpMethod.Get to "/api/groups",
        HttpMethod.Put to "/api/groups/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/groups/${UUID.randomUUID()}/members",
        HttpMethod.Post to "/api/groups/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Post to "/api/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Delete to "/api/groups/${UUID.randomUUID()}/subgroups/${UUID.randomUUID()}"
    )

    override fun componentsRegistration() {
        super.componentsRegistration()
        groupService = declareMock()
    }

    lateinit var groupService: GroupService

    @Test
    fun `responds with 200 and subgroups list`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()
        val subgroupId1 = UUID.randomUUID()
        val subgroupId2 = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } returns mockk {
                    every { id } returns groupId
                    every { organization.id } returns organizationId
                }
            every { groupService.getSubgroups(groupId) } returns listOf(
                mockk {
                    every { id } returns subgroupId1
                    every { name } returns "Subgroup1"
                    every { isImplicit } returns true
                    every { organization.id } returns organizationId
                },
                mockk {
                    every { id } returns subgroupId2
                    every { name } returns "Subgroup2"
                    every { isImplicit } returns false
                    every { organization.id } returns organizationId
                }
            )
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId/subgroups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val subgroups = assertNotNull(response.deserializeContent<GroupCollectionMessageBody>().data)
                assertEquals(2, subgroups.count())
                assertTrue { subgroups.any { it.id == subgroupId1 && it.name == "Subgroup1" } }
                assertTrue { subgroups.any { it.id == subgroupId2 && it.name == "Subgroup2" } }
            }
        }
    }

    @Test
    fun `responds to unknown group's subgroups request with 404 and error message`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } throws ValidationException(ValidationException.Reason.ResourceNotFound, "Group not found")
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId/subgroups")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("Group not found"))
            }
        }
    }

    @Test
    fun `responds to request for subgroups in organization not related to user with 403 and error message`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } returns mockk {
                every { id } returns groupId
                every { organization.id } returns organizationId
            }
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns UUID.randomUUID()
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId/subgroups")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue(response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user is not a member of an organization containing the group with the provided id"))
            }
        }
    }

    @Test
    fun `responds with 200 and the specified group`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } returns mockk {
                every { id } returns groupId
                every { name } returns "Group1"
                every { organization.id } returns organizationId
                every { isImplicit } returns false
            }
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val group = assertNotNull(response.deserializeContent<GroupMessageBody>().data)
                assertTrue { group.id == groupId && group.name == "Group1" && !group.isImplicit }
            }
        }
    }

    @Test
    fun `responds to request for group in organization not related to user with 403 and error message`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } returns mockk {
                every { id } returns groupId
                every { name } returns "Group1"
                every { organization.id } returns UUID.randomUUID()
                every { isImplicit } returns false
            }
            every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                mockk {
                    every { user.id } returns UUID.randomUUID()
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRoleDto.Reader
                })
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                assertTrue { response.deserializeContent<ErrorMessageBody>().error
                    .contains("The user is not a member of an organization containing the group with the provided id") }
            }
        }
    }

    @Test
    fun `responds to unknown group request with 404 and error message`() = withConfiguredTestApplication {
        val groupId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getGroup(groupId) } throws ValidationException(ValidationException.Reason.ResourceNotFound, "Group not found")
            with(handleRequest(HttpMethod.Get, "/api/groups/$groupId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue { response.deserializeContent<ErrorMessageBody>().error
                    .contains("Group not found") }
            }
        }
    }

}
