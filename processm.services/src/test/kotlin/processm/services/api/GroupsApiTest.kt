package processm.services.api

import io.ktor.http.*
import io.mockk.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedCollection
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.dbmodels.models.*
import processm.dbmodels.urn
import processm.services.api.models.ErrorMessage
import processm.services.api.models.Group
import processm.services.api.models.OrganizationRole
import processm.services.api.models.UserInfo
import processm.services.logic.*
import java.util.*
import java.util.stream.Stream
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupsApiTest : BaseApiTest() {
    override fun endpointsWithAuthentication() = Stream.of(
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/groups",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/members",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/members",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/subgroups/${UUID.randomUUID()}"
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Post to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/subgroups",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/groups/${UUID.randomUUID()}/subgroups/${UUID.randomUUID()}"
    )

    @Test
    fun `responds to organization groups request with 200 and groups list`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val accountService = declareMock<AccountService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val groupId1 = UUID.randomUUID()
        val groupId2 = UUID.randomUUID()

        every { accountService.getRolesAssignedToUser(userId) } returns
                listOf(mockk {
                    every { user.id } returns EntityID(userId, Users)
                    every { organization.id } returns EntityID(organizationId, Organizations)
                    every { this@mockk.role } returns RoleType.Reader.role
                })
        withAuthentication(userId, role = null) {
            every { organizationService.getOrganizationGroups(organizationId) } returns listOf(
                mockk {
                    every { id } returns EntityID(groupId1, Groups)
                    every { name } returns "Group1"
                    every { this@mockk.organizationId } returns mockk {
                        every { id } returns EntityID(organizationId, Organizations)
                    }
                    every { isImplicit } returns false
                    every { isShared } returns true
                },
                mockk {
                    every { id } returns EntityID(groupId2, Groups)
                    every { name } returns "Group2"
                    every { this@mockk.organizationId } returns mockk {
                        every { id } returns EntityID(organizationId, Organizations)
                    }
                    every { isImplicit } returns false
                    every { isShared } returns false

                }
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val groups = assertNotNull(response.deserializeContent<List<ApiGroup>>())

                assertEquals(2, groups.count())
                assertTrue { groups.any { it.id == groupId1 && it.name == "Group1" && !it.isImplicit!! && it.isShared!! } }
                assertTrue { groups.any { it.id == groupId2 && it.name == "Group2" && !it.isImplicit!! && !it.isShared!! } }
            }
        }
    }

    @Test
    fun `responds to random organization groups list request with 403 and error message`() =
        withConfiguredTestApplication {
            val unknownOrganizationId = UUID.randomUUID()

            withAuthentication {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$unknownOrganizationId/groups")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user is not a member of the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds to groups list of recently removed organization request with 404 and error message`() =
        withConfiguredTestApplication {
            val organizationService = declareMock<OrganizationService>()
            val accountService = declareMock<AccountService>()
            val removedOrganizationId = UUID.randomUUID()

            every { accountService.getRolesAssignedToUser(any()) } returns
                    listOf(mockk {
                        every { user.id } returns EntityID(UUID.randomUUID(), Users)
                        every { organization.id } returns EntityID(removedOrganizationId, Organizations)
                        every { this@mockk.role } returns RoleType.Reader.role
                    })
            withAuthentication(role = null) {
                every { organizationService.getOrganizationGroups(removedOrganizationId) } throws ValidationException(
                    Reason.ResourceNotFound,
                    userMessage = "Organization not found"
                )
                with(handleRequest(HttpMethod.Get, "/api/organizations/$removedOrganizationId/groups")) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("Organization not found")
                    )
                }
            }
        }

    @Test
    fun `responds with 201 to group creation`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { create(any(), null, organizationId, false) } returns mockk {
                every { id } returns EntityID(groupId, Groups)
                every { name } returnsArgument 1
                every { this@mockk.organizationId } returns mockk {
                    every { id } returns EntityID(organizationId, Organizations)
                }
                every { isImplicit } returns false
                every { isShared } returnsArgument 4
            }
        }

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/groups") {
                withSerializedBody(ApiGroup(name = "ABC"))
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
                val uri = response.headers["Location"]
                assertNotNull(uri)
                assertEquals(groupId.toString(), uri.substringAfterLast('/'))
                verify(exactly = 1) { groupService.create("ABC", null, organizationId, false) }
            }
        }
    }

    @Test
    fun `responds with 403 to group creation when creator has insufficient rights`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { create(any(), null, organizationId, true) } returns mockk {
                every { id } returns EntityID(groupId, Groups)
                every { name } returnsArgument 1
                every { this@mockk.organizationId } returns mockk {
                    every { id } returns EntityID(organizationId, Organizations)
                }
                every { isImplicit } returns false
                every { isShared } returnsArgument 4
            }
        }

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/groups") {
                withSerializedBody(ApiGroup(name = "ABC"))
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                val uri = response.headers["Location"]
                assertNull(uri)
            }
        }
    }

    @Test
    fun `responds with 204 to group update`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { update(any(), any()) } just runs
        }

        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/organizations/$organizationId/groups/$groupId") {
                withSerializedBody(ApiGroup(name = "new name"))
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
                verify(exactly = 1) { groupService.update(groupId, any()) }
            }
        }
    }

    @Test
    fun `responds with 403 to group when user has insufficient rights`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { update(any(), any()) } just runs
        }

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/organizations/$organizationId/groups/$groupId") {
                withSerializedBody(ApiGroup(name = "new name"))
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                verify(exactly = 0) { groupService.update(groupId, any()) }
            }
        }
    }

    @Test
    fun `responds with 204 to group delete`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { remove(groupId) } just runs
        }

        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/groups/$groupId")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
                verify(exactly = 1) { groupService.remove(groupId) }
            }
        }
    }

    @Test
    fun `responds with 404 to group delete if group does not exist`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { remove(groupId) } throws ValidationException(Reason.ResourceNotFound, "Group is not found.")
        }

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/groups/$groupId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                verify(exactly = 1) { groupService.remove(groupId) }
            }
        }
    }

    @Test
    fun `responds with 403 to group delete if user has insufficient rights`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { remove(groupId) } just runs
        }

        withAuthentication(role = OrganizationRole.none to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/groups/$groupId")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
                verify(exactly = 0) { groupService.remove(groupId) }
            }
        }
    }

    @Test
    fun `responds with 200 and subgroups list`() = withConfiguredTestApplication {
        val groupService = declareMock<GroupService>()
        val organizationService = declareMock<OrganizationService>()
        val groupId = UUID.randomUUID()
        val rootGroupId = UUID.randomUUID()
        val subgroupId1 = UUID.randomUUID()
        val subgroupId2 = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every { groupService.getRootGroupId(groupId) } returns rootGroupId
            every { organizationService.getOrganizationBySharedGroupId(rootGroupId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "org1"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
            every { groupService.getSubgroups(groupId) } returns listOf(
                mockk {
                    every { id } returns EntityID(subgroupId1, Groups)
                    every { name } returns "Subgroup1"
                    every { isImplicit } returns false
                    every { isShared } returns false
                    every { this@mockk.organizationId } returns mockk {
                        every { id } returns EntityID(organizationId, Organizations)
                    }
                },
                mockk {
                    every { id } returns EntityID(subgroupId2, Groups)
                    every { name } returns "Subgroup2"
                    every { isImplicit } returns false
                    every { isShared } returns false
                    every { this@mockk.organizationId } returns mockk {
                        every { id } returns EntityID(organizationId, Organizations)
                    }
                }
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/${UUID.randomUUID()}/groups/$groupId/subgroups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val subgroups = assertNotNull(response.deserializeContent<List<Group>>())
                assertEquals(2, subgroups.count())
                assertTrue { subgroups.any { it.id == subgroupId1 && it.name == "Subgroup1" && it.organizationId == organizationId } }
                assertTrue { subgroups.any { it.id == subgroupId2 && it.name == "Subgroup2" && it.organizationId == organizationId } }
            }
        }
    }

    @Test
    fun `responds to unknown group's subgroups request with 404 and error message`() = withConfiguredTestApplication {
        val groupService = declareMock<GroupService>()
        val groupId = UUID.randomUUID()

        withAuthentication {
            every { groupService.getRootGroupId(groupId) } throws ValidationException(
                Reason.ResourceNotFound,
                "The specified group does not exist"
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/${UUID.randomUUID()}/groups/$groupId/subgroups")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue(
                    response.deserializeContent<ErrorMessage>().error
                        .contains("The specified group does not exist")
                )
            }
        }
    }

    @Test
    fun `responds to request for subgroups in organization not related to user with 403 and error message`() =
        withConfiguredTestApplication {
            val groupService = declareMock<GroupService>()
            val organizationService = declareMock<OrganizationService>()
            val groupId = UUID.randomUUID()
            val rootGroupId = UUID.randomUUID()
            val orgId = UUID.randomUUID()

            withAuthentication {
                every { groupService.getRootGroupId(groupId) } returns rootGroupId
                every { organizationService.getOrganizationBySharedGroupId(rootGroupId) } returns mockk {
                    every { id } returns EntityID(orgId, Organizations)
                    every { name } returns "org1"
                    every { isPrivate } returns false
                    every { parentOrganization } returns null
                }
                with(handleRequest(HttpMethod.Get, "/api/organizations/$orgId/groups/$groupId/subgroups")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user is not a member of the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds with 200 and the specified group`() = withConfiguredTestApplication {
        val groupService = declareMock<GroupService>()
        val organizationService = declareMock<OrganizationService>()
        val groupId = UUID.randomUUID()
        val rootGroupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every { groupService.getRootGroupId(groupId) } returns rootGroupId
            every { organizationService.getOrganizationBySharedGroupId(rootGroupId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "org1"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
            every { groupService.getGroup(groupId) } returns mockk {
                every { id } returns EntityID(groupId, Groups)
                every { name } returns "Group1"
                every { isImplicit } returns false
                every { isShared } returns true
                every { this@mockk.organizationId } returns mockk {
                    every { id } returns EntityID(organizationId, Organizations)
                }
            }

            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups/$groupId")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val group = assertNotNull(response.deserializeContent<ApiGroup>())
                assertTrue { group.id == groupId && group.name == "Group1" && !group.isImplicit!! }
            }
        }
    }

    @Test
    fun `responds to request for group in organization not related to user with 403 and error message`() =
        withConfiguredTestApplication {
            val groupService = declareMock<GroupService>()
            val organizationService = declareMock<OrganizationService>()
            val groupId = UUID.randomUUID()
            val rootGroupId = UUID.randomUUID()
            val orgId = UUID.randomUUID()

            withAuthentication {
                every { groupService.getRootGroupId(groupId) } returns rootGroupId
                every { organizationService.getOrganizationBySharedGroupId(rootGroupId) } returns mockk {
                    every { id } returns EntityID(orgId, Organizations)
                    every { name } returns "org1"
                    every { isPrivate } returns false
                    every { parentOrganization } returns null
                }
                with(handleRequest(HttpMethod.Get, "/api/organizations/$orgId/groups/$groupId")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue {
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user is not a member of the related organization")
                    }
                }
            }
        }

    @Test
    fun `responds to unknown group request with 404 and error message`() = withConfiguredTestApplication {
        val groupService = declareMock<GroupService>()
        val organizationService = declareMock<OrganizationService>()
        val groupId = UUID.randomUUID()
        val rootGroupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            every { groupService.getRootGroupId(groupId) } returns rootGroupId
            every { organizationService.getOrganizationBySharedGroupId(rootGroupId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "org1"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
            every { groupService.getGroup(groupId) } throws ValidationException(
                Reason.ResourceNotFound,
                "The specified group does not exist"
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/${UUID.randomUUID()}/groups/$groupId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                assertTrue {
                    response.deserializeContent<ErrorMessage>().error
                        .contains("The specified group does not exist")
                }
            }
        }
    }

    @Test
    fun `responds with 200 to group members list`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { getGroup(groupId) } returns mockk {
                every { members } returns SizedCollection(listOf(
                    mockk {
                        every { id } returns EntityID(UUID.randomUUID(), Users)
                        every { email } returns "user@example.com"
                    },
                    mockk {
                        every { id } returns EntityID(UUID.randomUUID(), Users)
                        every { email } returns "user2@example.com"
                    }
                ))
            }
        }
        val organizationService = declareMock<OrganizationService> {
            every { get(organizationId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "Org 1"
            }
        }
        val accountService = declareMock<AccountService> {
            every { getRolesAssignedToUser(any()) } returns emptyList()
        }

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups/$groupId/members")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val users = response.deserializeContent<List<UserInfo>>()
                assertEquals(2, users.size)
                assertTrue(users.any { it.username == "user@example.com" })
                assertTrue(users.any { it.username == "user2@example.com" })
            }
        }
    }

    @Test
    fun `responds with 404 to group members list of nonexistent group`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { getGroup(groupId) } throws ValidationException(Reason.ResourceNotFound, "Group is not found.")
        }

        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups/$groupId/members")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `responds with 403 to group members list if the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val organizationId2 = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val groupService = declareMock<GroupService> {
                every { getGroup(groupId) } returns mockk {
                    every { members } returns SizedCollection(listOf(
                        mockk {
                            every { id } returns EntityID(UUID.randomUUID(), Users)
                            every { email } returns "user@example.com"
                        },
                        mockk {
                            every { id } returns EntityID(UUID.randomUUID(), Users)
                            every { email } returns "user2@example.com"
                        }
                    ))
                }
            }

            assertNotEquals(organizationId, organizationId2)
            withAuthentication(role = OrganizationRole.reader to organizationId2) {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups/$groupId/members")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            }
        }

    @Test
    fun `responds with 201 to the addition of a group member`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { attachUserToGroup(userId, groupId) } just runs
        }

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/groups/$groupId/members") {
                withSerializedBody(userId)
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
                val uri = response.headers["Location"]
                assertNotNull(uri)
                assertEquals(userId.toString(), uri.substringAfterLast('/'))

                verify(exactly = 1) { groupService.attachUserToGroup(userId, groupId) }
            }
        }
    }

    @Test
    fun `responds with 404 to the addition of a group member to nonexistent group`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { attachUserToGroup(userId, groupId) } throws ValidationException(
                Reason.ResourceNotFound,
                "Group is not found"
            )
        }

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/groups/$groupId/members") {
                withSerializedBody(userId)
            }) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `responds with 403 to the addition of a group member if the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val groupService = declareMock<GroupService> {
                every { attachUserToGroup(userId, groupId) } just runs
            }

            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/groups/$groupId/members") {
                    withSerializedBody(userId)
                }) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    verify(exactly = 0) { groupService.attachUserToGroup(groupId, userId) }
                }
            }
        }

    @Test
    fun `responds with 204 to the deletion of a group member`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val groupService = declareMock<GroupService> {
            every { detachUserFromGroup(userId, groupId) } just runs
        }

        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(
                handleRequest(
                    HttpMethod.Delete,
                    "/api/organizations/$organizationId/groups/$groupId/members/$userId"
                )
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())

                verify(exactly = 1) { groupService.detachUserFromGroup(userId, groupId) }
            }
        }
    }

    @Test
    fun `responds with 404 to the deletion of a group member if the member does not exist`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val groupService = declareMock<GroupService> {
                every { detachUserFromGroup(userId, groupId) } throws ValidationException(
                    Reason.ResourceNotFound,
                    "Member is not found."
                )
            }

            withAuthentication(role = OrganizationRole.writer to organizationId) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/groups/$groupId/members/$userId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }

    @Test
    fun `responds with 403 to the deletion of a group member if the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val groupId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val groupService = declareMock<GroupService> {
                every { detachUserFromGroup(userId, groupId) } throws ValidationException(
                    Reason.ResourceNotFound,
                    "Member is not found."
                )
            }

            withAuthentication(role = OrganizationRole.none to organizationId) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/groups/$groupId/members/$userId"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    verify(exactly = 0) { groupService.detachUserFromGroup(groupId, userId) }
                }
            }
        }

    @Test
    fun `responds with 200 to listing group sole ownership`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val e1 = EntityID(UUID.randomUUID(), Workspaces)
        val e2 = EntityID(UUID.randomUUID(), DataStores)
        declareMock<GroupService> {
            every { getSoleOwnershipURNs(groupId) } returns listOf(e1.urn, e2.urn)
        }

        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups/$groupId/sole-ownership")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val objects = response.deserializeContent<Array<ApiEntityID>>()
                assertEquals(2, objects.size)
                assertEquals(ApiEntityID(ApiEntityType.workspace, e1.value), objects[0])
                assertEquals(ApiEntityID(ApiEntityType.dataStore, e2.value), objects[1])
            }
        }
    }
}
