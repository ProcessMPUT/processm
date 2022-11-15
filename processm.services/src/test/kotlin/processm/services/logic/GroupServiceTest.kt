package processm.services.logic

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.junit.jupiter.api.assertDoesNotThrow
import processm.dbmodels.models.AccessControlList
import processm.dbmodels.models.Groups
import processm.dbmodels.models.Users
import processm.dbmodels.models.UsersInGroups
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

        assertEquals(Reason.ResourceNotFound, exception.reason)
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

        assertEquals(Reason.ResourceNotFound, exception.reason)
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

        assertEquals(Reason.ResourceNotFound, exception.reason)
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

        assertEquals(Reason.ResourceNotFound, exception.reason)
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

        assertEquals(Reason.ResourceNotFound, exception.reason)
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
}
