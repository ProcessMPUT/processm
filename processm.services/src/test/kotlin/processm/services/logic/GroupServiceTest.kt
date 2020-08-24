package processm.services.logic

import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.*

class GroupServiceTest : ServiceTestBase() {

    @Before
    @BeforeEach
    fun setUp() {
        groupService = GroupService()
    }

    lateinit var groupService: GroupService

    @Test
    fun `attachment of user to group throws if nonexistent user`(): Unit = withCleanTables(UserGroups, Users) {
        val groupId = createGroup()
        val userId = UUID.randomUUID()

        val exception = assertFailsWith<ValidationException>("Specified user or organization does not exist") {
            groupService.attachUserToGroup(userId, groupId.value)
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `attachment of user to group throws if nonexistent group`(): Unit = withCleanTables(UserGroups, Users) {
        val groupId = UUID.randomUUID()
        val userId = createUser()

        val exception = assertFailsWith<ValidationException>("Specified user or organization does not exist") {
            groupService.attachUserToGroup(userId.value, groupId)
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `attachment of already attached user to group returns`(): Unit = withCleanTables(UserGroups, Users) {
        val groupId = createGroup()
        val userId = createUser()

        assertDoesNotThrow { groupService.attachUserToGroup(userId.value, groupId.value) }
        assertDoesNotThrow { groupService.attachUserToGroup(userId.value, groupId.value) }

        assertEquals(1, UsersInGroups.select { UsersInGroups.userId eq userId and(UsersInGroups.groupId eq groupId) }.count())
    }

    @Test
    fun `successful attachment of user to group returns`(): Unit = withCleanTables(UserGroups, Users) {
        val groupId = createGroup()
        val userId = createUser()

        assertDoesNotThrow { groupService.attachUserToGroup(userId.value, groupId.value) }

        assertTrue { UsersInGroups.select { UsersInGroups.userId eq userId and(UsersInGroups.groupId eq groupId) }.any() }
    }

    @Test
    fun `getting specified group throws if nonexistent group`(): Unit = withCleanTables(UserGroups) {
        val exception = assertFailsWith<ValidationException>("Specified group does not exist") {
            groupService.getGroup(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `getting specified group returns`(): Unit = withCleanTables(UserGroups) {
        val groupId = createGroup(name = "Group1")

        val group = assertNotNull(groupService.getGroup(groupId.value))

        assertEquals("Group1", group.name)
    }

    @Test
    fun `getting subgroups throws if nonexistent group`(): Unit = withCleanTables(UserGroups) {
        val exception = assertFailsWith<ValidationException>("Specified group does not exist") {
            groupService.getSubgroups(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `getting subgroups returns`(): Unit = withCleanTables(UserGroups) {
        val groupId1 = createGroup(name = "Group1")
        val groupId2 = createGroup(name = "Group2")
        val subgroupId1 = createGroup(name = "Subgroup1", parentGroupId = groupId1.value)
        val subgroupId3 = createGroup(name = "Subgroup3", parentGroupId = groupId1.value)
        createGroup(name = "Subgroup2", parentGroupId = groupId2.value)

        val subgroups = assertNotNull(groupService.getSubgroups(groupId1.value))

        assertEquals(2, subgroups.count())
        assertTrue { subgroups.any { it.id == subgroupId1.value && it.name == "Subgroup1" } }
        assertTrue { subgroups.any { it.id == subgroupId3.value && it.name == "Subgroup3" } }
    }

    @Test
    fun `getting root group id throws if nonexistent group`() = withCleanTables(UserGroups) {
        val exception = assertFailsWith<ValidationException>("The specified group does not exist") {
            groupService.getRootGroupId(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `getting root group id returns the same group if parent`() = withCleanTables(UserGroups) {
        val groupId = createGroup(name = "Group")

        val rootGroupId = groupService.getRootGroupId(groupId.value)

        assertEquals(groupId.value, rootGroupId)
    }

    @Test
    fun `getting root group id returns`() = withCleanTables(UserGroups) {
        val groupId1 = createGroup(name = "Group1")
        val groupId2 = createGroup(name = "Group2", parentGroupId = groupId1.value)
        val groupId3 = createGroup(name = "Group3", parentGroupId = groupId2.value)

        val rootGroupId = groupService.getRootGroupId(groupId3.value)

        assertEquals(groupId1.value, rootGroupId)
    }
}
