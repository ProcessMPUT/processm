package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.assertThrows
import processm.core.models.metadata.URN
import processm.dbmodels.models.*
import processm.dbmodels.urn
import java.util.*
import kotlin.test.*

class OrganizationServiceTest : ServiceTestBase() {
    @Test
    fun `getting organization members throws if nonexistent organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getMembers(UUID.randomUUID())
        }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all members attached to organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations, Users, UsersRolesInOrganizations
    ) {
        val organization1 = createOrganization().id.value
        val organization2 = createOrganization().id.value
        val userId1 = createUser("user1@example.com").id.value
        val userId2 = createUser("user2@example.com").id.value
        val userId3 = createUser("user3@example.com").id.value
        attachUserToOrganization(userId1, organization1)
        attachUserToOrganization(userId2, organization2)
        attachUserToOrganization(userId3, organization1)

        val organizationMembers = organizationService.getMembers(organization1)

        assertEquals(2, organizationMembers.count())
        assertTrue { organizationMembers.any { it.user.email == "user1@example.com" } }
        assertTrue { organizationMembers.any { it.user.email == "user3@example.com" } }
    }

    @Test
    fun `getting organization groups throws if nonexistent organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getOrganizationGroups(UUID.randomUUID())
        }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all groups related to organization`(): Unit = withCleanTables(AccessControlList, Groups) {
        val organization1 = createOrganization()
        val organization2 = createOrganization()
        val groupId1 = createGroup(organizationId = organization2.id.value)
        val groupId2 = createGroup(organizationId = organization2.id.value)

        val organization1Groups = organizationService.getOrganizationGroups(organization1.id.value)
        assertEquals(1, organization1Groups.count())
        assertTrue { organization1Groups.any { it.id == organization1.sharedGroup.id } }

        val organization2Groups = organizationService.getOrganizationGroups(organization2.id.value)
        assertEquals(3, organization2Groups.count())
        assertTrue { organization2Groups.any { it.id == organization2.sharedGroup.id } }
        assertTrue { organization2Groups.any { it.id == groupId1 } }
        assertTrue { organization2Groups.any { it.id == groupId2 } }
    }

    @Test
    fun `returns organization related to to shared group`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val organizationId = createOrganization()
        createOrganization()

        val organization = organizationService.getOrganizationBySharedGroupId(organizationId.sharedGroup.id.value)
        assertEquals(organizationId.id, organization.id)
    }

    @Test
    fun `getting organization groups throws if nonexistent shared group`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception =
            assertFailsWith<ValidationException>("The specified shared group id is not assigned to any organization") {
                organizationService.getOrganizationBySharedGroupId(UUID.randomUUID())
            }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns public organizations only if public organizations requested`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val public = createOrganization(name = "public", isPrivate = false)
        val private = createOrganization(name = "private", isPrivate = true)

        val response = organizationService.getAll(true)
        assertEquals(response.size, 1)
        assertEquals(public.id, response[0].id)
        assertEquals("public", response[0].name)
        assertEquals(false, response[0].isPrivate)
    }

    @Test
    fun `returns all organizations only if public and private organizations requested`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val public = createOrganization(name = "public", isPrivate = false)
        val private = createOrganization(name = "private", isPrivate = true)

        val response = organizationService.getAll(false)
        assertEquals(response.size, 2)
        assertEquals(public.id, response[0].id)
        assertEquals("public", response[0].name)
        assertEquals(false, response[0].isPrivate)
        assertEquals(private.id, response[1].id)
        assertEquals("private", response[1].name)
        assertEquals(true, response[1].isPrivate)
    }

    @Test
    fun `attach a subOrganization`() {
        val grandparent = createOrganization(name = "grandparent", isPrivate = false).id.value
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true).id.value
        organizationService.attachSubOrganization(parent, child)
        organizationService.attachSubOrganization(grandparent, parent)
        assertNull(organizationService.get(grandparent).parentOrganization)
        assertEquals(grandparent, organizationService.get(parent).parentOrganization?.id?.value)
        assertEquals(parent, organizationService.get(child).parentOrganization?.id?.value)
    }

    @Test
    fun `organizations hierarchy is acyclic`() {
        val grandparent = createOrganization(name = "grandparent", isPrivate = false).id.value
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true).id.value
        organizationService.attachSubOrganization(parent, child)
        organizationService.attachSubOrganization(grandparent, parent)
        assertThrows<ValidationException> { organizationService.attachSubOrganization(child, grandparent) }
    }

    @Test
    fun `cannot reattach`() {
        val grandparent = createOrganization(name = "grandparent", isPrivate = false).id.value
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true).id.value
        organizationService.attachSubOrganization(grandparent, parent)
        organizationService.attachSubOrganization(parent, child)
        assertThrows<ValidationException> { organizationService.attachSubOrganization(grandparent, child) }
    }

    @Test
    fun `move the child to the top`() {
        val grandparent = createOrganization(name = "grandparent", isPrivate = false).id.value
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true).id.value
        organizationService.attachSubOrganization(parent, child)
        organizationService.detachSubOrganization(child)
        assertNull(organizationService.get(child).parentOrganization)
        organizationService.attachSubOrganization(grandparent, parent)
        organizationService.attachSubOrganization(child, grandparent)
        assertEquals(child, organizationService.get(grandparent).parentOrganization?.id?.value)
        assertEquals(grandparent, organizationService.get(parent).parentOrganization?.id?.value)
    }

    @Test
    fun `cannot detach a solitary organization`() {
        val org = createOrganization(name = "org", isPrivate = false).id.value
        assertThrows<ValidationException> { organizationService.detachSubOrganization(org) }
    }

    @Test
    fun `get subOrganizations`() {
        val grandparent = createOrganization(name = "grandparent", isPrivate = false).id.value
        val parent = createOrganization(name = "parent", isPrivate = false, parentOrganizationId = grandparent).id.value
        val child = createOrganization(name = "child", isPrivate = true, parentOrganizationId = parent).id.value
        with(organizationService.getSubOrganizations(grandparent)) {
            assertEquals(2, size)
            assertTrue { any { it.id.value == parent } }
            assertTrue { any { it.id.value == child } }
        }
        with(organizationService.getSubOrganizations(parent)) {
            assertEquals(1, size)
            assertTrue { any { it.id.value == child } }
        }
        with(organizationService.getSubOrganizations(child)) {
            assertTrue { isEmpty() }
        }
    }

    @Test
    fun `delete organization`() {
        val org = createOrganization("test")
        organizationService.remove(org.id.value)
    }

    @Test
    fun `delete sub-organization`() {
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true, parentOrganizationId = parent).id.value
        organizationService.remove(child)
    }

    @Test
    fun `delete an organization with a child`() {
        val parent = createOrganization(name = "parent", isPrivate = false).id.value
        val child = createOrganization(name = "child", isPrivate = true, parentOrganizationId = parent).id.value
        organizationService.remove(parent)
        assertNull(organizationService.get(child).parentOrganization)
    }

    @Test
    fun `delete an organization with a group and an ACL entry`() {
        val org = createOrganization(name = "org", isPrivate = false).id.value
        val group = groupService.create("group", organizationId = org).id.value
        val urn = URN("urn:processm:test/${UUID.randomUUID()}")
        aclService.addEntry(urn, group, RoleType.Reader)
        organizationService.remove(org)
        assertThrows<ValidationException> { groupService.getGroup(group) }
        assertThrows<ValidationException> { organizationService.get(org) }
        assertTrue { aclService.getEntries(urn).isEmpty() }
    }

    @Test
    fun `getSoleOwnershipURNs - user's ownership is not organization's ownership`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization()
            val user = createUser(organizationId = org.id.value)
            EntityID(createWorkspace("W1", user.id.value, org.id.value), Workspaces).urn
            assertTrue { organizationService.getSoleOwnershipURNs(org.id.value).isEmpty() }
        }

    @Test
    fun `getSoleOwnershipURNs - ownership shared between user and shared group`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization()
            val user = createUser(organizationId = org.id.value)
            val w1 = EntityID(createWorkspace("W1", user.id.value, org.id.value), Workspaces).urn
            aclService.updateEntry(w1, org.sharedGroup.id.value, RoleType.Owner)
            assertTrue { organizationService.getSoleOwnershipURNs(org.id.value).isEmpty() }
        }

    @Test
    fun `getSoleOwnershipURNs - shared group is the sole owner`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization()
            val user = createUser(organizationId = org.id.value)
            val w1 = EntityID(createWorkspace("W1", user.id.value, org.id.value), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org.id.value), Workspaces).urn
            aclService.updateEntry(w1, org.sharedGroup.id.value, RoleType.Owner)
            aclService.removeEntry(w1, user.privateGroup.id.value)
            with(organizationService.getSoleOwnershipURNs(org.id.value)) {
                assertEquals(1, size)
                assertTrue { w1 in this }
                assertTrue { w2 !in this }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - two groups from the same organization`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group1 = createGroup(organizationId = org).value
            val group2 = createGroup(organizationId = org).value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org), Workspaces).urn
            aclService.removeEntries(w1)
            aclService.removeEntries(w2)
            aclService.addEntry(w1, group1, RoleType.Owner)
            aclService.addEntry(w1, group2, RoleType.Owner)
            aclService.addEntry(w2, group2, RoleType.Owner)
            with(organizationService.getSoleOwnershipURNs(org)) {
                assertEquals(2, size)
                assertTrue { w1 in this }
                assertTrue { w2 in this }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - two groups from different organizations`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org1 = createOrganization().id.value
            val org2 = createOrganization().id.value
            val user = createUser(organizationId = org1)
            val group1 = createGroup(organizationId = org1).value
            val group2 = createGroup(organizationId = org2).value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org1), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org1), Workspaces).urn
            aclService.removeEntries(w1)
            aclService.removeEntries(w2)
            aclService.addEntry(w1, group1, RoleType.Owner)
            aclService.addEntry(w1, group2, RoleType.Owner)
            aclService.addEntry(w2, group2, RoleType.Owner)
            assertTrue { organizationService.getSoleOwnershipURNs(org1).isEmpty() }
            with(organizationService.getSoleOwnershipURNs(org2)) {
                assertEquals(1, size)
                assertTrue { w1 !in this }
                assertTrue { w2 in this }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - two groups from different organizations both can read both`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org1 = createOrganization().id.value
            val org2 = createOrganization().id.value
            val user = createUser(organizationId = org1)
            val group1 = createGroup(organizationId = org1).value
            val group2 = createGroup(organizationId = org2).value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org1), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org1), Workspaces).urn
            aclService.removeEntries(w1)
            aclService.removeEntries(w2)
            aclService.addEntry(w1, group1, RoleType.Owner)
            aclService.addEntry(w2, group1, RoleType.Reader)
            aclService.addEntry(w1, group2, RoleType.Reader)
            aclService.addEntry(w2, group2, RoleType.Owner)
            with(organizationService.getSoleOwnershipURNs(org1)) {
                assertEquals(1, size)
                assertTrue { w1 in this }
                assertTrue { w2 !in this }
            }
            with(organizationService.getSoleOwnershipURNs(org2)) {
                assertEquals(1, size)
                assertTrue { w1 !in this }
                assertTrue { w2 in this }
            }
        }

}
