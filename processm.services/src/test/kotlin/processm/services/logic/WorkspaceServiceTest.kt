package processm.services.logic

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.koin.test.mock.declareMock
import processm.services.models.*
import java.util.*
import kotlin.test.*

class WorkspaceServiceTest : ServiceTestBase() {

    @Before
    @BeforeEach
    fun setUp() {
        accountServiceMock = mockk()
        workspaceService = WorkspaceService(accountServiceMock)
    }

    lateinit var accountServiceMock: AccountService
    lateinit var workspaceService: WorkspaceService

    @Test
    fun `returns all user workspaces in the organization`(): Unit = withCleanTables(Users, UserGroups, Workspaces, Organizations, UserGroupWithWorkspaces, UsersInGroups) {
        val userGroupId = createGroup()
        val otherUserGroupId = createGroup()
        val userId = createUser(privateGroupId = userGroupId.value)
        val workspaceId1 = createWorkspace("Workspace1")
        val workspaceId2 = createWorkspace("Workspace2")
        val workspaceId3 = createWorkspace("Workspace3")
        val workspaceId4 = createWorkspace("Workspace4")
        val organizationId = createOrganization()
        attachUserGroupToWorkspace(userGroupId.value, workspaceId1.value, organizationId.value)
        attachUserGroupToWorkspace(userGroupId.value, workspaceId2.value)
        attachUserGroupToWorkspace(userGroupId.value, workspaceId3.value, organizationId.value)
        attachUserGroupToWorkspace(otherUserGroupId.value, workspaceId4.value, organizationId.value)

        val userWorkspaces = workspaceService.getUserWorkspaces(userId.value, organizationId.value)

        assertEquals(2, userWorkspaces.size)
        assertTrue { userWorkspaces.any { it.name == "Workspace1" } }
        assertTrue { userWorkspaces.any { it.name == "Workspace3" } }
    }

    @Test
    fun `successful user workspace creation returns`(): Unit = withCleanTables(Users, UserGroups, Workspaces, Organizations, UserGroupWithWorkspaces, UsersInGroups) {
        val userGroupId = createGroup()
        val userId = createUser(privateGroupId = userGroupId.value)
        val organizationId = createOrganization()

        every { accountServiceMock.getAccountDetails(userId.value) } returns mockk {
            every { privateGroup.id } returns userGroupId.value
        }

        val workspaceId = workspaceService.createWorkspace("Workspace1", userId.value, organizationId.value)

        assertTrue { Workspaces.select { Workspaces.id eq workspaceId and (Workspaces.name eq "Workspace1") }.any() }
        assertTrue { UserGroupWithWorkspaces.select { UserGroupWithWorkspaces.workspaceId eq workspaceId and (UserGroupWithWorkspaces.userGroupId eq userGroupId) and (UserGroupWithWorkspaces.organizationId eq organizationId) }.any() }
    }

    @Test
    fun `successful user workspace removal returns true`(): Unit = withCleanTables(Users, UserGroups, Workspaces, Organizations, UserGroupWithWorkspaces, UsersInGroups) {
        val userGroupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = userGroupId.value)
        val workspaceId = createWorkspace("Workspace1")
        val organizationId = createOrganization()
        attachUserGroupToWorkspace(userGroupId.value, workspaceId.value, organizationId.value)

        assertTrue { workspaceService.removeWorkspace(workspaceId.value, userId.value, organizationId.value) }
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.empty() }
    }

    @Test
    fun `user workspace removal fails if user has insufficient permissions`(): Unit = withCleanTables(Users, UserGroups, Workspaces, Organizations, UserGroupWithWorkspaces, UsersInGroups) {
        val userGroupId = createGroup(groupRole = GroupRoleDto.Reader)
        val userId = createUser(privateGroupId = userGroupId.value)
        val workspaceId = createWorkspace("Workspace1")
        val organizationId = createOrganization()
        attachUserGroupToWorkspace(userGroupId.value, workspaceId.value, organizationId.value)

        val exception =
            assertFailsWith<ValidationException>("The specified workspace does not exist or the user has insufficient permissions to it") {
                workspaceService.removeWorkspace(workspaceId.value, userId.value, organizationId.value)
            }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.any() }
    }

    @Test
    fun `user workspace removal fails if workspace does not exist`(): Unit = withCleanTables(Users, UserGroups, Workspaces, Organizations, UserGroupWithWorkspaces, UsersInGroups) {
        val workspaceId = createWorkspace("Workspace1")

        val exception =
            assertFailsWith<ValidationException>("The specified workspace does not exist or the user has insufficient permissions to it") {
                workspaceService.removeWorkspace(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.any() }
    }
}
