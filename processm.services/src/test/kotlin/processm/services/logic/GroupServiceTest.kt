package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import processm.dbmodels.models.*
import processm.dbmodels.urn
import processm.services.helpers.ExceptionReason
import java.util.*
import kotlin.test.*

class GroupServiceTest : ServiceTestBase() {
    @Test
    fun `attachment of user to group throws if nonexistent user`(): Unit = withCleanTables(
        AccessControlList, Groups, Users
    ) {
        val groupId = createGroup()
        val userId = UUID.randomUUID()

        val exception = assertFailsWith<ValidationException>("Specified user or organization does not exist") {
            groupService.attachUserToGroup(userId, groupId.value)
        }

        assertEquals(ExceptionReason.USER_OR_GROUP_NOT_FOUND, exception.reason)
    }

    @Test
    fun `attachment of user to group throws if nonexistent group`(): Unit = withCleanTables(
        AccessControlList, Groups, Users
    ) {
        val groupId = UUID.randomUUID()
        val userId = createUser().id.value

        val exception = assertFailsWith<ValidationException>("Specified user or organization does not exist") {
            groupService.attachUserToGroup(userId, groupId)
        }

        assertEquals(ExceptionReason.USER_OR_GROUP_NOT_FOUND, exception.reason)
    }

    @Test
    fun `attachment of already attached user to group returns`(): Unit = withCleanTables(
        AccessControlList, Groups, Users
    ) {
        val groupId = createGroup().value
        val userId = createUser().id.value

        assertDoesNotThrow { groupService.attachUserToGroup(userId, groupId) }
        assertDoesNotThrow { groupService.attachUserToGroup(userId, groupId) }

        assertEquals(
            1,
            UsersInGroups.select { UsersInGroups.userId eq userId and (UsersInGroups.groupId eq groupId) }.count()
        )
    }

    @Test
    fun `successful attachment of user to group returns`(): Unit = withCleanTables(
        AccessControlList, Groups, Users
    ) {
        val groupId = createGroup().value
        val userId = createUser().id.value

        assertDoesNotThrow { groupService.attachUserToGroup(userId, groupId) }

        assertTrue {
            UsersInGroups.select { UsersInGroups.userId eq userId and (UsersInGroups.groupId eq groupId) }.any()
        }
    }

    @Test
    fun `getting specified group throws if nonexistent group`(): Unit = withCleanTables(AccessControlList, Groups) {
        val exception = assertFailsWith<ValidationException>("Specified group does not exist") {
            groupService.getGroup(UUID.randomUUID())
        }

        assertEquals(ExceptionReason.GROUP_NOT_FOUND, exception.reason)
    }

    @Test
    fun `getting specified group returns`(): Unit = withCleanTables(AccessControlList, Groups) {
        val groupId = createGroup(name = "Group1")

        val group = assertNotNull(groupService.getGroup(groupId.value))

        assertEquals("Group1", group.name)
    }

    @Test
    fun `getting subgroups throws if nonexistent group`(): Unit = withCleanTables(AccessControlList, Groups) {
        val exception = assertFailsWith<ValidationException>("Specified group does not exist") {
            groupService.getSubgroups(UUID.randomUUID())
        }

        assertEquals(ExceptionReason.GROUP_NOT_FOUND, exception.reason)
    }

    @Test
    fun `getting subgroups returns`(): Unit = withCleanTables(AccessControlList, Groups) {
        val groupId1 = createGroup(name = "Group1")
        val groupId2 = createGroup(name = "Group2")
        val subgroupId1 = createGroup(name = "Subgroup1", parentGroupId = groupId1.value)
        val subgroupId3 = createGroup(name = "Subgroup3", parentGroupId = groupId1.value)
        createGroup(name = "Subgroup2", parentGroupId = groupId2.value)

        val subgroups = assertNotNull(groupService.getSubgroups(groupId1.value))

        assertEquals(2, subgroups.count())
        assertTrue { subgroups.any { it.id.value == subgroupId1.value && it.name == "Subgroup1" } }
        assertTrue { subgroups.any { it.id.value == subgroupId3.value && it.name == "Subgroup3" } }
    }

    @Test
    fun `getting root group id throws if nonexistent group`() = withCleanTables(AccessControlList, Groups) {
        val exception = assertFailsWith<ValidationException>("The specified group does not exist") {
            groupService.getRootGroupId(UUID.randomUUID())
        }

        assertEquals(ExceptionReason.GROUP_NOT_FOUND, exception.reason)
    }

    @Test
    fun `getting root group id returns the same group if parent`() = withCleanTables(AccessControlList, Groups) {
        val groupId = createGroup(name = "Group")

        val rootGroupId = groupService.getRootGroupId(groupId.value)

        assertEquals(groupId.value, rootGroupId)
    }

    @Test
    fun `getting root group id returns`() = withCleanTables(AccessControlList, Groups) {
        val groupId1 = createGroup(name = "Group1")
        val groupId2 = createGroup(name = "Group2", parentGroupId = groupId1.value)
        val groupId3 = createGroup(name = "Group3", parentGroupId = groupId2.value)

        val rootGroupId = groupService.getRootGroupId(groupId3.value)

        assertEquals(groupId1.value, rootGroupId)
    }

    @Test
    fun `cannot deatch user from their implicit group`(): Unit =
        withCleanTables(AccessControlList, UsersInGroups, Users, Groups) {
            val user = createUser()
            assertThrows<ValidationException> {
                groupService.detachUserFromGroup(user.id.value, user.privateGroup.id.value)
            }
        }

    @Test
    fun `cannot detach user from a shared group`(): Unit =
        withCleanTables(AccessControlList, UsersInGroups, Users, Groups, Organizations) {
            val org = createOrganization()
            val user = createUser(organizationId = org.id.value)
            assertThrows<ValidationException> {
                groupService.detachUserFromGroup(user.id.value, org.sharedGroup.id.value)
            }
        }

    @Test
    fun `getSoleOwnershipURNs - user is the sole owner of two workspaces`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val w1 = EntityID(createWorkspace("W1", user.id.value, org), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org), Workspaces).urn
            with(groupService.getSoleOwnershipURNs(user.privateGroup.id.value)) {
                assertEquals(2, size)
                assertTrue { w1 in this }
                assertTrue { w2 in this }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - user shares the ownership of one of two workspaces with a group`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group = createGroup().value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org), Workspaces).urn
            aclService.addEntry(w1, group, RoleType.Owner)
            with(groupService.getSoleOwnershipURNs(user.privateGroup.id.value)) {
                assertEquals(1, size)
                assertTrue { w1 !in this }
                assertTrue { w2 in this }
            }
            with(groupService.getSoleOwnershipURNs(group)) {
                assertTrue { isEmpty() }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - user is the sole owner of two workspaces but there's a reader`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group = createGroup().value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org), Workspaces).urn
            aclService.addEntry(w1, group, RoleType.Reader)
            with(groupService.getSoleOwnershipURNs(user.privateGroup.id.value)) {
                assertEquals(2, size)
                assertTrue { w1 in this }
                assertTrue { w2 in this }
            }
            with(groupService.getSoleOwnershipURNs(group)) {
                assertTrue { isEmpty() }
            }
        }

    @Test
    fun `getSoleOwnershipURNs - user owns one workspace, group owns another workspace, both can read both`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group = createGroup().value
            val w1 = EntityID(createWorkspace("W1", user.id.value, org), Workspaces).urn
            val w2 = EntityID(createWorkspace("W2", user.id.value, org), Workspaces).urn
            aclService.addEntry(w1, group, RoleType.Owner)
            aclService.removeEntry(w1, user.privateGroup.id.value)
            aclService.addEntry(w2, group, RoleType.Reader)
            aclService.addEntry(w1, user.privateGroup.id.value, RoleType.Reader)
            with(groupService.getSoleOwnershipURNs(user.privateGroup.id.value)) {
                assertEquals(1, size)
                assertTrue { w1 !in this }
                assertTrue { w2 in this }
            }
            with(groupService.getSoleOwnershipURNs(group)) {
                assertEquals(1, size)
                assertTrue { w1 in this }
                assertTrue { w2 !in this }
            }
        }

    @Test
    fun `remove does not removes dangling objects`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group = createGroup(organizationId = org).value
            val w1 = createWorkspace("W1", user.id.value, org)
            val w2 = createWorkspace("W2", user.id.value, org)
            val urn1 = EntityID(w1, Workspaces).urn
            val urn2 = EntityID(w2, Workspaces).urn
            aclService.addEntry(urn1, group, RoleType.Owner)
            aclService.removeEntry(urn1, user.privateGroup.id.value)
            aclService.addEntry(urn2, group, RoleType.Reader)
            aclService.addEntry(urn1, user.privateGroup.id.value, RoleType.Reader)
            try {
                groupService.remove(group)
                assertFalse(true)
            } catch (e: ValidationException) {
                assertEquals(ExceptionReason.GROUP_IS_SOLE_OWNER, e.reason)
            }
            assertFalse { aclService.getEntries(urn1).isEmpty() }
            assertFalse { Workspaces.select { (Workspaces.id eq w1) and (Workspaces.deleted eq false) }.empty() }
            assertEquals(group, groupService.getGroup(group).id.value)
        }

    @Test
    fun `remove succeeds if there are no dangling objects`(): Unit =
        withCleanTables(AccessControlList, Groups, UsersInGroups, Workspaces) {
            val org = createOrganization().id.value
            val user = createUser(organizationId = org)
            val group = createGroup(organizationId = org).value
            val w1 = createWorkspace("W1", user.id.value, org)
            val w2 = createWorkspace("W2", user.id.value, org)
            val urn1 = EntityID(w1, Workspaces).urn
            val urn2 = EntityID(w2, Workspaces).urn
            aclService.addEntry(urn1, group, RoleType.Owner)
            aclService.removeEntry(urn1, user.privateGroup.id.value)
            aclService.addEntry(urn2, group, RoleType.Reader)
            aclService.addEntry(urn1, user.privateGroup.id.value, RoleType.Reader)
            workspaceService.remove(w1)
            assertEquals(group, groupService.getGroup(group).id.value)
            groupService.remove(group)
            assertThrows<ValidationException> { groupService.getGroup(group) }
        }
}
