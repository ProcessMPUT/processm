package processm.services.api

import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.core.models.metadata.URN
import processm.dbmodels.models.Group
import processm.dbmodels.models.Groups
import processm.dbmodels.models.Organizations
import processm.dbmodels.models.RoleType
import processm.services.api.models.OrganizationRole
import processm.services.helpers.ExceptionReason
import processm.services.logic.ACLService
import processm.services.logic.ValidationException
import java.util.*
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ACLAPITest : BaseApiTest() {
    override fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>?> = Stream.of(null)

    override fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>?> = Stream.of(null)

    @Test
    fun `read ACL of an owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk(relaxed = true) {
                    every { id } returns EntityID(organizationId, Groups)
                    every { this@mockk.organizationId } returns mockk(relaxed = true) {
                        every { id } returns EntityID(organizationId, Organizations)
                        every { parentOrganization } returns null
                    }
                }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            },
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk(relaxed = true) {
                    every { id } returns EntityID(groupId, Groups)
                    every { this@mockk.organizationId } returns mockk(relaxed = true) {
                        every { id } returns EntityID(organizationId, Organizations)
                        every { parentOrganization } returns null
                    }
                }
                every { role } returns mockk { every { name } returns RoleType.Writer }
            }
        )
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/acl/${urn.urn}")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val entries = response.deserializeContent<List<APIAccessControlEntry>>()
                assertEquals(2, entries.size)
                assertEquals(organizationId, entries[0].groupId)
                assertEquals(OrganizationRole.owner, entries[0].role)
                assertEquals(groupId, entries[1].groupId)
                assertEquals(OrganizationRole.writer, entries[1].role)
            }
        }
    }

    @Test
    fun `read ACL of a non-owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns false
        withAuthentication(userId, role = OrganizationRole.reader to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/acl/${urn.urn}")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `add entry to an owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.addEntry(urn, groupId, RoleType.Reader) } returns mockk { }
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/acl/${urn.urn}") {
                withSerializedBody(APIAccessControlEntry(groupId, OrganizationRole.reader))
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
        verify(exactly = 1) { aclService.addEntry(urn, groupId, RoleType.Reader) }
    }

    @Test
    fun `fail to add entry for a group that already has an entry`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.addEntry(urn, groupId, RoleType.Reader) } throws mockk<ExposedSQLException>(relaxed = true)
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/acl/${urn.urn}") {
                withSerializedBody(APIAccessControlEntry(groupId, OrganizationRole.reader))
            }) {
                assertEquals(HttpStatusCode.Conflict, response.status())
            }
        }
        verify(exactly = 1) { aclService.addEntry(urn, groupId, RoleType.Reader) }
    }

    @Test
    fun `fail to add entry to a non-owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns false
        every { aclService.addEntry(urn, groupId, RoleType.Reader) } returns mockk { }
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Post, "/api/acl/${urn.urn}") {
                withSerializedBody(APIAccessControlEntry(groupId, OrganizationRole.reader))
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
        verify(exactly = 0) { aclService.addEntry(urn, groupId, RoleType.Reader) }
    }

    @Test
    fun `delete entry from an owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val base64urn = urn.urn
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(organizationId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            },
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(groupId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            }
        )
        every { aclService.removeEntry(urn, groupId) } returns null
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/acl/ace/$groupId/$base64urn")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
        verify(exactly = 1) { aclService.removeEntry(urn, groupId) }
    }

    @Test
    fun `fail to delete entry from a non-owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns false
        every { aclService.removeEntry(urn, groupId) } returns null
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/acl/ace/$groupId/${urn.urn}")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
        verify(exactly = 0) { aclService.removeEntry(urn, groupId) }
    }

    @Test
    fun `fail to delete the last owner of an object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(groupId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            }
        )
        every { aclService.removeEntry(urn, organizationId) } returns null
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Delete, "/api/acl/ace/$groupId/${urn.urn}")) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
        verify(exactly = 0) { aclService.removeEntry(urn, organizationId) }
    }

    @Test
    fun `update an entry in an owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(organizationId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            },
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(groupId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            }
        )
        every { aclService.updateEntry(urn, groupId, RoleType.Reader) } returns mockk()
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/acl/ace/$groupId/${urn.urn}") {
                withSerializedBody(OrganizationRole.reader)
            }) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
        verify(exactly = 1) { aclService.updateEntry(urn, groupId, RoleType.Reader) }
    }

    @Test
    fun `it is impossible to downgrade the last ACE capable of modifying an object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(organizationId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            },
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(groupId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Reader }
            }
        )
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/acl/ace/$organizationId/${urn.urn}") {
                withSerializedBody(OrganizationRole.reader)
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
        verify(exactly = 0) { aclService.updateEntry(urn, organizationId, RoleType.Reader) }
    }

    @Test
    fun `update non-existing entry in an owned object`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        every { aclService.getEntries(urn) } returns listOf(
            mockk {
                every { this@mockk.urn } returns urn
                every { group } returns mockk { every { id } returns EntityID(organizationId, Groups) }
                every { role } returns mockk { every { name } returns RoleType.Owner }
            }
        )
        every {
            aclService.updateEntry(urn, groupId, RoleType.Reader)
        } throws ValidationException(ExceptionReason.ACENotFound)
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Put, "/api/acl/ace/$groupId/${urn.urn}") {
                withSerializedBody(OrganizationRole.reader)
            }) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
        verify(exactly = 1) { aclService.updateEntry(urn, groupId, RoleType.Reader) }
    }

    @Test
    fun `get available groups`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        val data = listOf(mockk<Group> {
            every { id } returns EntityID(groupId, Groups)
            every { name } returns "Group name"
            every { this@mockk.organizationId } returns mockk(relaxed = true) {
                every { id } returns EntityID(organizationId, Organizations)
            }
            every { isImplicit } returns false
            every { isShared } returns true
        })
        every { aclService.getAvailableGroups(urn, userId) } returns mockk {
            every { iterator() } returns data.iterator()
            every { count() } returns data.size.toLong()
        }
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/acl/available-groups/${urn.urn}")) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
        verify(exactly = 1) { aclService.getAvailableGroups(urn, userId) }
    }

    @Test
    fun `can modify`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns true
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/acl/can-modify/${urn.urn}")) {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }
        }
    }

    @Test
    fun `cannot modify`() = withConfiguredTestApplication {
        val userId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val urn = URN("urn:processm:db/Table/object")
        val aclService = declareMock<ACLService>()
        every { aclService.hasPermission(userId, urn, RoleType.Owner) } returns false
        withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
            with(handleRequest(HttpMethod.Get, "/api/acl/can-modify/${urn.urn}")) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }
}