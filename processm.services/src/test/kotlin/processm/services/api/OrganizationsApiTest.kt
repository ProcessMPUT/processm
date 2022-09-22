package processm.services.api

import io.ktor.http.*
import io.mockk.*
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.dbmodels.models.User
import processm.dbmodels.models.Users
import processm.services.api.models.ErrorMessage
import processm.services.api.models.Group
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.AccountService
import processm.services.logic.OrganizationService
import processm.services.logic.Reason
import processm.services.logic.ValidationException
import java.util.*
import java.util.stream.Stream
import kotlin.test.Test
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
        HttpMethod.Patch to "/api/organizations/${UUID.randomUUID()}/members/${UUID.randomUUID()}",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}/groups",
    )

    override fun endpointsWithNoImplementation() = Stream.of(
        HttpMethod.Get to "/api/organizations",
        HttpMethod.Post to "/api/organizations",
        HttpMethod.Get to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Put to "/api/organizations/${UUID.randomUUID()}",
        HttpMethod.Delete to "/api/organizations/${UUID.randomUUID()}",
    )

    @Test
    fun `responds to organization groups request with 200 and groups list`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val accountService = declareMock<AccountService>()
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
                },
                mockk {
                    every { id } returns groupId2
                    every { name } returns "Group2"
                    every { isImplicit } returns false
                }
            )
            every { accountService.getRolesAssignedToUser(userId) } returns listOf(
                mockk {
                    every { user.id } returns userId
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRole.reader
                })
            with(handleRequest(HttpMethod.Get, "/api/organizations/$organizationId/groups")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val groups = assertNotNull(response.deserializeContent<List<Group>>())

                assertEquals(2, groups.count())
                assertTrue { groups.any { it.id == groupId1 && it.name == "Group1" && it.isImplicit } }
                assertTrue { groups.any { it.id == groupId2 && it.name == "Group2" && !it.isImplicit } }
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

            withAuthentication {
                every { organizationService.getOrganizationGroups(removedOrganizationId) } throws ValidationException(
                    Reason.ResourceNotFound,
                    userMessage = "Organization not found"
                )
                every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                    mockk {
                        every { user.id } returns UUID.randomUUID()
                        every { organization.id } returns removedOrganizationId
                        every { this@mockk.role } returns OrganizationRole.reader
                    })
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
    fun `responds to organization members request with 200 and members list`() = withConfiguredTestApplication {
        val organizationService = declareMock<OrganizationService>()
        val accountService = declareMock<AccountService>()
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberId1 = UUID.randomUUID()
        val memberId2 = UUID.randomUUID()

        withAuthentication(userId) {
            every { organizationService.getMember(organizationId) } returns listOf(
                mockk {
                    every { user.id } returns memberId1
                    every { user.email } returns "user1@example.com"
                    every { role } returns OrganizationRole.reader
                },
                mockk {
                    every { user.id } returns memberId2
                    every { user.email } returns "user2@example.com"
                    every { role } returns OrganizationRole.writer
                }
            )
            every { accountService.getRolesAssignedToUser(userId) } returns listOf(
                mockk {
                    every { user.id } returns userId
                    every { organization.id } returns organizationId
                    every { this@mockk.role } returns OrganizationRole.reader
                })
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

            withAuthentication {
                every { organizationService.getMember(removedOrganizationId) } throws ApiException(
                    publicMessage = "",
                    responseCode = HttpStatusCode.NotFound
                )
                every { accountService.getRolesAssignedToUser(any()) } returns listOf(
                    mockk {
                        every { user.id } returns UUID.randomUUID()
                        every { organization.id } returns removedOrganizationId
                        every { this@mockk.role } returns OrganizationRole.reader
                    })
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
                organizationService.addMember(organizationId, "new@example.com", OrganizationRole.reader)
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
            organizationService.addMember(organizationId, "new@example.com", OrganizationRole.reader)
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
                organizationService.addMember(organizationId, email, OrganizationRole.reader)
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
            organizationService.addMember(organizationId, email, OrganizationRole.reader)
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
                organizationService.updateMember(organizationId, memberToUpdate, OrganizationRole.writer)
            } just runs

            with(handleRequest(HttpMethod.Patch, "/api/organizations/$organizationId/members/$memberToUpdate") {
                withSerializedBody(OrganizationMember(organizationRole = OrganizationRole.writer))
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }

        verify(exactly = 1) {
            organizationService.updateMember(organizationId, memberToUpdate, OrganizationRole.writer)
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
                organizationService.updateMember(organizationId, memberToUpdate, OrganizationRole.writer)
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
            organizationService.updateMember(organizationId, memberToUpdate, OrganizationRole.writer)
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
}
