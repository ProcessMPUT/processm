package processm.services.logic

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `returns all user workspaces in the organization`(): Unit = withCleanTables(
        Users, UserGroups,
        Workspaces, Organizations,
        UserGroupWithWorkspaces, UsersInGroups
    ) {
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
    fun `successful user workspace creation returns`(): Unit = withCleanTables(
        Users, UserGroups,
        Workspaces, Organizations,
        UserGroupWithWorkspaces, UsersInGroups
    ) {
        val userGroupId = createGroup()
        val userId = createUser(privateGroupId = userGroupId.value)
        val organizationId = createOrganization()

        every { accountServiceMock.getAccountDetails(userId.value) } returns mockk {
            every { privateGroup.id } returns userGroupId.value
        }

        val workspaceId = workspaceService.createWorkspace("Workspace1", userId.value, organizationId.value)

        assertTrue { Workspaces.select { Workspaces.id eq workspaceId and (Workspaces.name eq "Workspace1") }.any() }
        assertTrue {
            UserGroupWithWorkspaces.select { UserGroupWithWorkspaces.workspaceId eq workspaceId and (UserGroupWithWorkspaces.userGroupId eq userGroupId) and (UserGroupWithWorkspaces.organizationId eq organizationId) }
                .any()
        }
    }

    @Test
    fun `successful user workspace removal returns true`(): Unit = withCleanTables(
        Users, UserGroups,
        Workspaces, Organizations,
        UserGroupWithWorkspaces, UsersInGroups
    ) {
        val userGroupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = userGroupId.value)
        val workspaceId = createWorkspace("Workspace1")
        val organizationId = createOrganization()
        attachUserGroupToWorkspace(userGroupId.value, workspaceId.value, organizationId.value)

        assertTrue { workspaceService.removeWorkspace(workspaceId.value, userId.value, organizationId.value) }
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.empty() }
    }

    @Test
    fun `user workspace removal fails if user has insufficient permissions`(): Unit = withCleanTables(
        Users, UserGroups,
        Workspaces, Organizations,
        UserGroupWithWorkspaces, UsersInGroups
    ) {
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
    fun `user workspace removal fails if workspace does not exist`(): Unit = withCleanTables(
        Users, UserGroups,
        Workspaces, Organizations,
        UserGroupWithWorkspaces, UsersInGroups
    ) {
        val workspaceId = createWorkspace("Workspace1")

        val exception =
            assertFailsWith<ValidationException>("The specified workspace does not exist or the user has insufficient permissions to it") {
                workspaceService.removeWorkspace(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.any() }
    }

    @Test
    fun `returns all user workspace components`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId1 = createWorkspace("Workspace1")
        attachUserGroupToWorkspace(groupId.value, workspaceId1.value, organizationId.value)
        val workspaceId2 = createWorkspace("Workspace2")
        val componentId1 =
            createWorkspaceComponent("Component1", workspaceId1.value, componentType = ComponentTypeDto.Kpi)
        val componentId2 =
            createWorkspaceComponent("Component2", workspaceId2.value, componentType = ComponentTypeDto.Kpi)
        val componentId3 =
            createWorkspaceComponent("Component3", workspaceId1.value, componentType = ComponentTypeDto.Kpi)

        val workspaceComponents =
            workspaceService.getWorkspaceComponents(workspaceId1.value, userId.value, organizationId.value)

        assertEquals(2, workspaceComponents.size)
        assertTrue { workspaceComponents.any { it.id == componentId1.value } }
        assertTrue { workspaceComponents.any { it.id == componentId3.value } }
    }

    @Test
    fun `returns only user workspace components with existing data source`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId = createWorkspace("Workspace1")
        val componentDataSourceId = DBSerializer.insert(DBCache.get(workspaceId.toString()), MutableCausalNet())
        attachUserGroupToWorkspace(groupId.value, workspaceId.value, organizationId.value)
        val componentWithNotExistingDataSource =
            createWorkspaceComponent("Component1", workspaceId.value, componentType = ComponentTypeDto.CausalNet)
        val componentWithExistingDataSource = createWorkspaceComponent(
            "Component2",
            workspaceId.value,
            componentType = ComponentTypeDto.CausalNet,
            dataSourceId = componentDataSourceId
        )

        val workspaceComponents =
            workspaceService.getWorkspaceComponents(workspaceId.value, userId.value, organizationId.value)

        assertEquals(1, workspaceComponents.size)
        assertTrue { workspaceComponents.any { it.id == componentWithExistingDataSource.value } }
    }

    @Test
    fun `successful workspace component update returns`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId = createWorkspace("Workspace1")
        attachUserGroupToWorkspace(groupId.value, workspaceId.value, organizationId.value)
        val componentId =
            createWorkspaceComponent("Component1", workspaceId.value, componentType = ComponentTypeDto.CausalNet)
        val newComponentName = "newName"
        val newComponentType = ComponentTypeDto.Kpi
        val newComponentCustomizationData = """{"data":"new"}"""
        val newDataQuery = "new query"

        workspaceService.addOrUpdateWorkspaceComponent(
            componentId.value,
            workspaceId.value,
            userId.value,
            organizationId.value,
            newComponentName,
            newDataQuery,
            newComponentType,
            newComponentCustomizationData
        )

        assertTrue {
            WorkspaceComponents.select {
                WorkspaceComponents.id eq componentId and
                        (WorkspaceComponents.name eq newComponentName) and
                        (WorkspaceComponents.query eq newDataQuery) and
                        (WorkspaceComponents.componentType eq newComponentType.typeName) and
                        (WorkspaceComponents.customizationData eq newComponentCustomizationData)
            }.any()
        }
    }

    @Test
    fun `skips workspace component field update if new value is null`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val oldComponentName = "oldName"
        val oldComponentType = ComponentTypeDto.Kpi
        val oldComponentCustomizationData = """{"data":"new"}"""
        val oldDataQuery = "query"
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId = createWorkspace("Workspace1")
        attachUserGroupToWorkspace(groupId.value, workspaceId.value, organizationId.value)
        val componentId = createWorkspaceComponent(
            oldComponentName,
            workspaceId.value,
            query = oldDataQuery,
            componentType = oldComponentType,
            customizationData = oldComponentCustomizationData
        )

        workspaceService.addOrUpdateWorkspaceComponent(
            componentId.value,
            workspaceId.value,
            userId.value,
            organizationId.value,
            name = null,
            query = null,
            componentType = null,
            customizationData = null
        )

        assertTrue {
            WorkspaceComponents.select {
                WorkspaceComponents.id eq componentId and
                        (WorkspaceComponents.name eq oldComponentName) and
                        (WorkspaceComponents.query eq oldDataQuery) and
                        (WorkspaceComponents.componentType eq oldComponentType.typeName) and
                        (WorkspaceComponents.customizationData eq oldComponentCustomizationData)
            }.any()
        }
    }

    @Test
    fun `workspace component field update fails if user has insufficient permissions`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val oldComponentName = "oldName"
        val oldComponentType = ComponentTypeDto.Kpi
        val oldComponentCustomizationData = """{"data":"new"}"""
        val oldDataQuery = "query"
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Reader)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId = createWorkspace("Workspace1")
        attachUserGroupToWorkspace(groupId.value, workspaceId.value, organizationId.value)
        val componentId = createWorkspaceComponent(
            oldComponentName,
            workspaceId.value,
            query = oldDataQuery,
            componentType = oldComponentType,
            customizationData = oldComponentCustomizationData
        )

        val exception =
            assertFailsWith<ValidationException>("The specified workspace component does not exist or the user has insufficient permissions to it") {
                workspaceService.addOrUpdateWorkspaceComponent(
                    componentId.value,
                    workspaceId.value,
                    userId.value,
                    organizationId.value,
                    name = null,
                    query = null,
                    componentType = null,
                    customizationData = null
                )
            }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `creates workspace component if the component does not exist`(): Unit = withCleanTables(
        Organizations, Users, UserGroups, UsersInGroups,
        WorkspaceComponents,
        Workspaces
    ) {
        val componentId = UUID.randomUUID();
        val componentName = "oldName"
        val componentType = ComponentTypeDto.Kpi
        val componentCustomizationData = """{"data":"new"}"""
        val dataQuery = "query"
        val organizationId = createOrganization()
        val groupId = createGroup(groupRole = GroupRoleDto.Writer)
        val userId = createUser(privateGroupId = groupId.value)
        val workspaceId = createWorkspace("Workspace1")
        attachUserGroupToWorkspace(groupId.value, workspaceId.value, organizationId.value)

        workspaceService.addOrUpdateWorkspaceComponent(
            componentId,
            workspaceId.value,
            userId.value,
            organizationId.value,
            name = componentName,
            query = dataQuery,
            componentType = componentType,
            customizationData = componentCustomizationData
        )

        assertTrue {
            WorkspaceComponents.select {
                WorkspaceComponents.id eq componentId and
                        (WorkspaceComponents.name eq componentName) and
                        (WorkspaceComponents.query eq dataQuery) and
                        (WorkspaceComponents.componentType eq componentType.typeName) and
                        (WorkspaceComponents.customizationData eq componentCustomizationData)
            }.any()
        }
    }
}
