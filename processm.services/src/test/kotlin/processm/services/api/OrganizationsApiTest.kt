package processm.services.api

import io.ktor.http.*
import io.mockk.*
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.dbmodels.models.*
import processm.dbmodels.urn
import processm.helpers.mapToSet
import processm.services.api.models.ErrorMessage
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.*
import java.util.*
import java.util.stream.Stream
import kotlin.test.*

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
        HttpMethod.Patch to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}/suborganizations/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}/suborganizations/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/suborganizations",
    )

    override fun endpointsWithNoImplementation() = Stream.empty<Pair<HttpMethod, String>>()

    @Test
    fun `responds to organization members request with 200 and members list`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val accountService = declareMock<AccountService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberId1 = UUID.randomUUID()
        val memberId2 = UUID.randomUUID()

        every { accountService.getRolesAssignedToUser(userId) } returns
                listOf(mockk {
                    every { user.id } returns EntityID(userId, Users)
                    every { organization.id } returns EntityID(organizationId, Organizations)
                    every { role } returns RoleType.Reader.role
                })
        withAuthentication(userId, role = null) {
            every { organizationService.getMembers(organizationId) } returns listOf(
                mockk {
                    every { user.id } returns EntityID(memberId1, Users)
                    every { user.email } returns "user1@example.com"
                    every { role } returns RoleType.Reader.role
                },
                mockk {
                    every { user.id } returns EntityID(memberId2, Users)
                    every { user.email } returns "user2@example.com"
                    every { role } returns RoleType.Writer.role
                }
            )
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/members")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val members = assertNotNull(response.deserializeContent<List<OrganizationMember>>())

                assertEquals(2, members.count())
                assertTrue { members.any { it.id == memberId1 && it.email == "user1@example.com" && it.organizationRole == OrganizationRole.reader } }
                assertTrue { members.any { it.id == memberId2 && it.email == "user2@example.com" && it.organizationRole == OrganizationRole.writer } }
            }
        }
    }

    @Test
    fun `responds to random organization members list request with 403 and error message`() =
        withConfiguredTestApplication {
            val unknownOrganizationId = UUID.randomUUID()

            withAuthentication {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$unknownOrganizationId/members")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    assertTrue(
                        response.deserializeContent<ErrorMessage>().error
                            .contains("The user is not a member of the related organization")
                    )
                }
            }
        }

    @Test
    fun `responds to members list of recently removed organization request with 404 and error message`() =
        withConfiguredTestApplication {
            val organizationService = declareMock<OrganizationService>()
            val accountService = declareMock<AccountService>()
            val removedOrganizationId = UUID.randomUUID()

            every { accountService.getRolesAssignedToUser(any()) } returns
                    listOf(mockk {
                        every { user.id } returns EntityID(UUID.randomUUID(), Users)
                        every { organization.id } returns EntityID(removedOrganizationId, Organizations)
                        every { role } returns RoleType.Reader.role
                    })

            withAuthentication(role = null) {
                every { organizationService.getMembers(removedOrganizationId) } throws ApiException(
                    publicMessage = "",
                    responseCode = HttpStatusCode.NotFound
                )

                with(handleRequest(HttpMethod.Get, "/api/organizations/$removedOrganizationId/members")) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }

    @Test
    fun `responds to the addition of a new member with 201`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            every {
                organizationService.addMember(organizationId, "new@example.com", RoleType.Reader)
            } returns mockk<User> {
                every { id } returns EntityID(UUID.randomUUID(), Users)
            }

            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/members") {
                withSerializedBody(
                    OrganizationMember(
                        email = "new@example.com",
                        organizationRole = OrganizationRole.reader
                    )
                )
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
            }
        }

        verify(exactly = 1) {
            organizationService.addMember(organizationId, "new@example.com", RoleType.Reader)
        }
    }

    @Test
    fun `responds to the addition of an existing member with 409`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val email = "user@example.com"

        withAuthentication(userId = userId, login = email, role = OrganizationRole.owner to organizationId) {
            every {
                organizationService.addMember(organizationId, email, RoleType.Reader)
            } throws ValidationException(
                Reason.ResourceAlreadyExists,
                "User already exists in the organization."
            )

            with(handleRequest(HttpMethod.Post, "/api/organizations/$organizationId/members") {
                withSerializedBody(
                    OrganizationMember(
                        email = email,
                        organizationRole = OrganizationRole.reader
                    )
                )
            }) {
                assertEquals(HttpStatusCode.Conflict, response.status())
            }
        }

        verify(exactly = 1) {
            organizationService.addMember(organizationId, email, RoleType.Reader)
        }
    }

    @Test
    fun `responds to the deletion of a member with 204`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberToDelete = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            every {
                organizationService.removeMember(organizationId, memberToDelete)
            } just runs

            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/members/$memberToDelete")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

        verify(exactly = 1) {
            organizationService.removeMember(organizationId, memberToDelete)
        }
    }

    @Test
    fun `responds to the deletion of non-member member with 404`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberToDelete = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            every {
                organizationService.removeMember(organizationId, memberToDelete)
            } throws ValidationException(Reason.ResourceNotFound, "User is not found.")

            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/members/$memberToDelete")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                val error = response.deserializeContent<ErrorMessage>()
                assertEquals("User is not found.", error.error)
            }
        }

        verify(exactly = 1) {
            organizationService.removeMember(organizationId, memberToDelete)
        }
    }

    @Test
    fun `responds to the deletion of the current user with 422`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId/members/$userId") {
                withSerializedBody(OrganizationMember(organizationRole = OrganizationRole.writer))
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
                val error = response.deserializeContent<ErrorMessage>()
                assertEquals("Cannot delete the current user.", error.error)
            }
        }

        verify(exactly = 0) {
            organizationService.removeMember(any(), any())
        }
    }

    @Test
    fun `responds to the update of a member role with 204`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberToUpdate = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            every {
                organizationService.updateMember(organizationId, memberToUpdate, RoleType.Writer)
            } just runs

            with(handleRequest(HttpMethod.Patch, "/api/organizations/$organizationId/members/$memberToUpdate") {
                withSerializedBody(OrganizationMember(organizationRole = OrganizationRole.writer))
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

        verify(exactly = 1) {
            organizationService.updateMember(organizationId, memberToUpdate, RoleType.Writer)
        }
    }

    @Test
    fun `responds to the update of a role of non-existing member with 404`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberToUpdate = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            every {
                organizationService.updateMember(organizationId, memberToUpdate, RoleType.Writer)
            } throws ValidationException(Reason.ResourceNotFound, "User is not found.")

            with(handleRequest(HttpMethod.Patch, "/api/organizations/$organizationId/members/$memberToUpdate") {
                withSerializedBody(OrganizationMember(organizationRole = OrganizationRole.writer))
            }) {
                assertEquals(HttpStatusCode.NotFound, response.status())
                val error = response.deserializeContent<ErrorMessage>()
                assertEquals("User is not found.", error.error)
            }
        }

        verify(exactly = 1) {
            organizationService.updateMember(organizationId, memberToUpdate, RoleType.Writer)
        }
    }

    @Test
    fun `responds to the update of a role of the current user with 422`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        withAuthentication(userId = userId, role = OrganizationRole.owner to organizationId) {

            with(handleRequest(HttpMethod.Patch, "/api/organizations/$organizationId/members/$userId") {
                withSerializedBody(OrganizationMember(organizationRole = OrganizationRole.writer))
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
                val error = response.deserializeContent<ErrorMessage>()
                assertEquals("Cannot change role of the current user.", error.error)
            }
        }

        verify(exactly = 0) {
            organizationService.updateMember(any(), any(), any())
        }
    }

    @Test
    fun `responds with 200 to the get of organization list`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val orgIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val organizationService = declareMock<OrganizationService> {
            every { getAll(userId) } returns
                    mockk {
                        every { iterator() } returns
                                listOf(
                                    mockk<Organization> {
                                        every { id } returns EntityID(orgIds[0], Organizations)
                                        every { name } returns "OrgA"
                                        every { isPrivate } returns false
                                        every { parentOrganization } returns null
                                    },
                                    mockk<Organization> {
                                        every { id } returns EntityID(orgIds[1], Organizations)
                                        every { name } returns "OrgB"
                                        every { isPrivate } returns false
                                        every { parentOrganization } returns null
                                    }
                                ).iterator()
                    }

            every { get(orgIds[0]) } returns mockk {
                every { id } returns EntityID(orgIds[0], Organizations)
                every { name } returns "OrgA"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
        }
        withAuthentication(userId = userId, role = OrganizationRole.owner to orgIds[0]) {
            with(handleRequest(HttpMethod.Get, "/api/organizations")) {
                assertEquals(HttpStatusCode.OK, response.status())

                val orgs = response.deserializeContent<List<ApiOrganization>>()
                assertEquals(2, orgs.size)
                assertTrue(orgs.any { org -> orgIds[0] == org.id && "OrgA" == org.name && !org.isPrivate })
                assertTrue(orgs.any { org -> orgIds[1] == org.id && "OrgB" == org.name && !org.isPrivate })

                verify(exactly = 1) { organizationService.getAll(userId) }
            }
        }
    }

    @Test
    fun `responds with 201 to the creation of an organization`() = withConfiguredTestApplication {
        val ownerId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { create("my org", false, ownerUserId = ownerId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "my org"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
        }
        withAuthentication(userId = ownerId) {
            with(handleRequest(HttpMethod.Post, "/api/organizations") {
                withSerializedBody(ApiOrganization(name = "my org", isPrivate = false))
            }) {
                assertEquals(HttpStatusCode.Created, response.status())
                val org = response.deserializeContent<processm.services.api.models.Organization>()
                assertEquals(organizationId, org.id)
                assertNull(org.parentOrganizationId)
                assertFalse(org.isPrivate)
                assertEquals("my org", org.name)

                verify(exactly = 1) { organizationService.create("my org", false, ownerUserId = ownerId) }
            }
        }
    }

    @Test
    fun `responds with 200 to the organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { get(organizationId) } returns mockk {
                every { id } returns EntityID(organizationId, Organizations)
                every { name } returns "my org"
                every { isPrivate } returns false
                every { parentOrganization } returns null
            }
        }
        withAuthentication(role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val org = response.deserializeContent<ApiOrganization>()
                assertEquals(organizationId, org.id)
                assertEquals("my org", org.name)
            }
        }
    }

    @Test
    fun `responds with 403 to the nonexistent organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { get(organizationId) } throws ValidationException(Reason.ResourceNotFound, "Not found.")
        }
        withAuthentication {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId")) {
                // As we require an authenticated user and the user sees only the organizations he/she belongs to,
                // we cannot distinguish between 403 and 404. Since the organization does not exist, it cannot be
                // included in the user's authentication token, and so we return 403.
                assertEquals(HttpStatusCode.Forbidden, response.status())
                verify(exactly = 0) { organizationService.get(organizationId) }
            }
        }
    }

    @Test
    fun `responds with 403 to the organization that the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
                every { get(organizationId) } throws ValidationException(Reason.ResourceNotFound, "Not found.")
            }
            withAuthentication(role = OrganizationRole.none to organizationId) {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId")) {
                    // As we require an authenticated user and the user sees only the organizations he/she belongs to,
                    // we cannot distinguish between 403 and 404. Since the organization does not exist, it cannot be
                    // included in the user's authentication token, and so we return 403.
                    assertEquals(HttpStatusCode.Forbidden, response.status())
                    verify(exactly = 0) { organizationService.get(organizationId) }
                }
            }
        }

    @Test
    fun `responds with 200 to the update of an organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { update(organizationId, any()) } just runs
        }
        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/organizations/$organizationId") {
                withSerializedBody(
                    ApiOrganization(
                        name = "new name",
                        isPrivate = false
                    )
                )
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())

                verify(exactly = 1) { organizationService.update(organizationId, any()) }
            }
        }
    }

    @Test
    fun `responds with 404 to the update of a nonexistent organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { update(organizationId, any()) } throws ValidationException(Reason.ResourceNotFound, "Not found.")
        }
        withAuthentication(role = OrganizationRole.writer to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/organizations/$organizationId") {
                withSerializedBody(
                    ApiOrganization(
                        name = "new name",
                        isPrivate = false
                    )
                )
            }) {
                assertEquals(HttpStatusCode.NotFound, response.status())

                verify(exactly = 1) { organizationService.update(organizationId, any()) }
            }
        }
    }

    @Test
    fun `responds with 403 to the update of an organization if the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
                every { update(organizationId, any()) } just runs
            }
            withAuthentication(role = OrganizationRole.reader to organizationId) {
                with(handleRequest(HttpMethod.Put, "/api/organizations/$organizationId")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.update(organizationId, any()) }
                }
            }
        }

    @Test
    fun `responds with 200 to the deletion of an organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { remove(organizationId) } just runs
        }
        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId")) {
                assertEquals(HttpStatusCode.NoContent, response.status())

                verify(exactly = 1) { organizationService.remove(organizationId) }
            }
        }
    }

    @Test
    fun `responds with 404 to the deletion of an nonexistent organization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { remove(organizationId) } throws ValidationException(Reason.ResourceNotFound, "Not found.")
        }
        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId")) {
                assertEquals(HttpStatusCode.NotFound, response.status())

                verify(exactly = 1) { organizationService.remove(organizationId) }
            }
        }
    }

    @Test
    fun `responds with 403 to the deletion of an organization if the caller has insufficient rights`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
                every { remove(organizationId) } just runs
            }
            withAuthentication(role = OrganizationRole.writer to organizationId) {
                with(handleRequest(HttpMethod.Delete, "/api/organizations/$organizationId")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.remove(organizationId) }
                }
            }
        }

    @Test
    fun `responds with 204 to attaching an organization as a suborganization`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val subOrganizationId = UUID.randomUUID()
        val organizationService = declareMock<OrganizationService> {
            every { attachSubOrganization(organizationId, subOrganizationId) } just runs
            every { get(organizationId) } returns mockk {
                every { parentOrganization } returns null
            }
            every { get(subOrganizationId) } returns mockk {
                every { parentOrganization } returns null
            }
        }
        withAuthentication(
            roles = arrayOf(OrganizationRole.writer to organizationId, OrganizationRole.owner to subOrganizationId)
        ) {
            with(
                handleRequest(
                    HttpMethod.Put,
                    "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                )
            ) {
                assertEquals(HttpStatusCode.NoContent, response.status())

                verify(exactly = 1) { organizationService.attachSubOrganization(organizationId, subOrganizationId) }
            }
        }
    }

    @Test
    fun `responds with 403 to attaching an organization as a suborganization to a reader in the parent`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
                every { attachSubOrganization(organizationId, subOrganizationId) } just runs
                every { get(organizationId) } returns mockk {
                    every { parentOrganization } returns null
                }
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns null
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.reader to organizationId, OrganizationRole.owner to subOrganizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Put,
                        "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.attachSubOrganization(organizationId, subOrganizationId) }
                }
            }
        }


    @Test
    fun `responds with 403 to attaching an organization as a suborganization to a writer in the child`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
                every { attachSubOrganization(organizationId, subOrganizationId) } just runs
                every { get(organizationId) } returns mockk {
                    every { parentOrganization } returns null
                }
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns null
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.owner to organizationId, OrganizationRole.writer to subOrganizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Put,
                        "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.attachSubOrganization(organizationId, subOrganizationId) }
                }
            }
        }

    @Test
    fun `responds with 204 to deattaching a suborganization by a writer in the parent`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val parent = mockk<Organization> {
                every { id } returns EntityID(organizationId, Organizations)
                every { parentOrganization } returns null
            }
            val organizationService = declareMock<OrganizationService> {
                every { detachSubOrganization(subOrganizationId) } just runs
                every { get(organizationId) } returns parent
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns parent
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.writer to organizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NoContent, response.status())

                    verify(exactly = 1) { organizationService.detachSubOrganization(subOrganizationId) }
                }
            }
        }

    @Test
    fun `responds with 403 to deattaching a suborganization by a reader in the parent`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val parent = mockk<Organization> {
                every { id } returns EntityID(organizationId, Organizations)
                every { parentOrganization } returns null
            }
            val organizationService = declareMock<OrganizationService> {
                every { detachSubOrganization(subOrganizationId) } just runs
                every { get(organizationId) } returns parent
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns parent
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.reader to organizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.detachSubOrganization(subOrganizationId) }
                }
            }
        }


    @Test
    fun `responds with 204 to deattaching a suborganization by an owner in the child`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val parent = mockk<Organization> {
                every { id } returns EntityID(organizationId, Organizations)
                every { parentOrganization } returns null
            }
            val organizationService = declareMock<OrganizationService> {
                every { detachSubOrganization(subOrganizationId) } just runs
                every { get(organizationId) } returns parent
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns parent
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.owner to subOrganizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$organizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NoContent, response.status())

                    verify(exactly = 1) { organizationService.detachSubOrganization(subOrganizationId) }
                }
            }
        }

    @Test
    fun `responds with 400 to deattaching a suborganization by a writer in another organization`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val otherOrganizationId = UUID.randomUUID()
            val subOrganizationId = UUID.randomUUID()
            val parent = mockk<Organization> {
                every { id } returns EntityID(organizationId, Organizations)
                every { parentOrganization } returns null
            }
            val organizationService = declareMock<OrganizationService> {
                every { detachSubOrganization(subOrganizationId) } just runs
                every { get(organizationId) } returns parent
                every { get(subOrganizationId) } returns mockk {
                    every { parentOrganization } returns parent
                }
            }
            withAuthentication(
                roles = arrayOf(OrganizationRole.owner to otherOrganizationId)
            ) {
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/organizations/$otherOrganizationId/suborganizations/$subOrganizationId"
                    )
                ) {
                    assertEquals(HttpStatusCode.BadRequest, response.status())

                    verify(exactly = 0) { organizationService.detachSubOrganization(subOrganizationId) }
                }
            }
        }

    @Test
    fun `responds with 200 to reading suborganizations`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val parent = mockk<Organization> {
                every { id } returns EntityID(organizationId, Organizations)
                every { parentOrganization } returns null
            }
            val suborgs = listOf(
                mockk<Organization>(relaxed = true) {
                    every { id } returns EntityID(UUID.randomUUID(), Organizations)
                    every { parentOrganization } returns parent
                },
                mockk<Organization>(relaxed = true) {
                    every { id } returns EntityID(UUID.randomUUID(), Organizations)
                    every { parentOrganization } returns parent
                },
            )
            val organizationService = declareMock<OrganizationService> {
                every { getSubOrganizations(organizationId) } returns suborgs
            }
            withAuthentication(
                role = OrganizationRole.reader to organizationId
            ) {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/suborganizations")) {
                    assertEquals(HttpStatusCode.OK, response.status())

                    verify(exactly = 1) { organizationService.getSubOrganizations(organizationId) }

                    val result = response.deserializeContent<List<ApiOrganization>>()
                    assertEquals(suborgs.size, result.size)
                    assertEquals(suborgs.mapToSet { it.id.value }, result.mapToSet { it.id })
                }
            }
        }

    @Test
    fun `fail to read suborganizations without a role`() =
        withConfiguredTestApplication {
            val organizationId = UUID.randomUUID()
            val organizationService = declareMock<OrganizationService> {
            }
            withAuthentication(
                role = OrganizationRole.none to organizationId
            ) {
                with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/suborganizations")) {
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                    verify(exactly = 0) { organizationService.getSubOrganizations(organizationId) }
                }
            }
        }

    @Test
    fun `responds with 200 to listing organization sole ownership`() = withConfiguredTestApplication {
        val organizationId = UUID.randomUUID()
        val e1 = EntityID(UUID.randomUUID(), Workspaces)
        val e2 = EntityID(UUID.randomUUID(), DataStores)
        declareMock<OrganizationService> {
            every { getSoleOwnershipURNs(organizationId) } returns listOf(e1.urn, e2.urn)
        }

        withAuthentication(role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/sole-ownership")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val objects = response.deserializeContent<Array<ApiEntityID>>()
                assertEquals(2, objects.size)
                assertEquals(ApiEntityID(ApiEntityType.workspace, e1.value), objects[0])
                assertEquals(ApiEntityID(ApiEntityType.dataStore, e2.value), objects[1])
            }
        }
    }
}
