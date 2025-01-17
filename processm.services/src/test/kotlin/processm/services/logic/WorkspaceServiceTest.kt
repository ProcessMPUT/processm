package processm.services.logic

import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import processm.core.models.causalnet.DBSerializer
import processm.core.models.causalnet.MutableCausalNet
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import processm.services.helpers.ExceptionReason
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceServiceTest : ServiceTestBase() {
    @Test
    fun `returns all user workspaces in the organization`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organization = createOrganization(name = "Org1")
        val userId = createUser(organizationId = organization.id.value).id.value
        val userId2 = createUser(userEmail = "user2@example.com", organizationId = organization.id.value).id.value
        val otherGroupId = createGroup("some group", organizationId = organization.id.value).value
        groupService.attachUserToGroup(userId, otherGroupId)
        // owner
        val workspace1 = createWorkspace("Workspace1", userId = userId, organizationId = organization.id.value)
        // ACL-based access through shared group
        val workspace2 = createWorkspace("Workspace2", userId = userId2, organizationId = organization.id.value)
        // ACL-based access through other group
        val workspace3 = createWorkspace("Workspace3", userId = userId2, organizationId = organization.id.value)
        // no access
        val workspace4 = createWorkspace("Workspace4", userId = userId2, organizationId = organization.id.value)

        // use ACL service to set permission to workspace2
        aclService.updateEntry(Workspaces, workspace2, organization.sharedGroup.id.value, RoleType.Reader)
        // use ACL service to set permission to workspace3
        aclService.addEntry(Workspaces, workspace3, otherGroupId, RoleType.Writer)

        val userWorkspaces = workspaceService.getUserWorkspaces(userId)

        assertEquals(3, userWorkspaces.size)
        assertTrue { userWorkspaces.any { it.first.name == "Workspace1" && it.second == RoleType.Owner } }
        assertTrue { userWorkspaces.any { it.first.name == "Workspace2" && it.second == RoleType.Reader } }
        assertTrue { userWorkspaces.any { it.first.name == "Workspace3" && it.second == RoleType.Writer } }
    }

    @Test
    fun `multiple ACLs to a single workspace do not lead to listing it multiple time`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organization = createOrganization(name = "Org1")
        val userId = createUser(organizationId = organization.id.value).id.value
        val otherGroupId = createGroup("some group", organizationId = organization.id.value).value
        groupService.attachUserToGroup(userId, otherGroupId)
        // owner
        val workspace = createWorkspace("Workspace1", userId = userId, organizationId = organization.id.value)
        // ACL-based access through shared group
        aclService.updateEntry(Workspaces, workspace, organization.sharedGroup.id.value, RoleType.Reader)
        // ACL-based access through other group
        aclService.addEntry(Workspaces, workspace, otherGroupId, RoleType.Reader)

        val userWorkspaces = workspaceService.getUserWorkspaces(userId)

        assertEquals(1, userWorkspaces.size)
        assertEquals("Workspace1", userWorkspaces[0].first.name)
    }

    @Test
    fun `successful workspace creation returns`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        Workspaces
    ) {
        val user = accountService.create("user@example.com", pass = "P@ssw0rd!")
        val org = organizationService.create("Org1", false)
        val workspaceId = workspaceService.create("Workspace1", user.id.value, org.id.value)

        val workspaces = workspaceService.getUserWorkspaces(user.id.value)
        assertEquals(1, workspaces.size)
        assertEquals(workspaceId, workspaces[0].first.id.value)
    }

    @Test
    fun `successful workspace removal returns true`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        Workspaces
    ) {
        val user = accountService.create("user@example.com", pass = "P@ssw0rd!")
        val org = organizationService.create("Org1", false)
        val workspaceId = workspaceService.create("Workspace1", user.id.value, org.id.value)

        workspaceService.remove(workspaceId)

        val workspaces = workspaceService.getUserWorkspaces(user.id.value)
        assertEquals(0, workspaces.size)
    }

    @Test
    fun `user workspace removal fails if workspace does not exist`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val user = createUser()
        val organizationId = createOrganization().id.value
        val workspaceId = createWorkspace("Workspace1", user.id.value, organizationId)

        val exception =
            assertFailsWith<ValidationException>("The specified workspace does not exist or the user has insufficient permissions to it") {
                workspaceService.remove(UUID.randomUUID())
            }
        assertEquals(ExceptionReason.WorkspaceNotFound, exception.reason)
        assertTrue { Workspaces.select { Workspaces.id eq workspaceId }.any() }
    }

    @Test
    fun `returns all user workspace components`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organizationId = createOrganization().id.value
        val user = createUser()
        val user2 = createUser(userEmail = "user2@example.com")
        val workspaceId1 = createWorkspace("Workspace1", user.id.value, organizationId)
        val workspaceId2 = createWorkspace("Workspace2", user2.id.value, organizationId)
        val componentId1 = createWorkspaceComponent("Component1", workspaceId1, componentType = ComponentTypeDto.Kpi)
        val componentId2 = createWorkspaceComponent("Component2", workspaceId2, componentType = ComponentTypeDto.Kpi)
        val componentId3 = createWorkspaceComponent("Component3", workspaceId1, componentType = ComponentTypeDto.Kpi)

        val workspaceComponents = workspaceService.getComponents(workspaceId1)

        assertEquals(2, workspaceComponents.size)
        assertTrue { workspaceComponents.any { it.id.value == componentId1.value } }
        assertTrue { workspaceComponents.any { it.id.value == componentId3.value } }
    }

    @Test
    fun `returns all user workspace components including invalid`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organizationId = createOrganization().id.value
        val user = createUser()
        val dataStoreId = createDataStore().value
        val workspaceId = createWorkspace("Workspace1", user.id.value, organizationId)
        val componentId = DBSerializer.insert(DBCache.get(dataStoreId.toString()).database, MutableCausalNet())
        val componentWithInvalidData =
            createWorkspaceComponent("Cmp1", workspaceId, componentType = ComponentTypeDto.CausalNet, data = "-1")
        val componentWithExistingId = createWorkspaceComponent(
            "Component2",
            workspaceId,
            componentType = ComponentTypeDto.CausalNet,
            data = componentId.toString()
        )

        val workspaceComponents = workspaceService.getComponents(workspaceId)

        assertEquals(2, workspaceComponents.size)
        assertTrue { workspaceComponents.any { it.id.value == componentWithExistingId.value } }
    }

    @Test
    fun `successful workspace component update returns`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organizationId = createOrganization().id.value
        val user = createUser()
        val workspaceId = createWorkspace("Workspace1", user.id.value, organizationId)
        val componentId =
            createWorkspaceComponent("Component1", workspaceId, componentType = ComponentTypeDto.CausalNet)
        val newComponentName = "newName"
        val newComponentType = ComponentTypeDto.Kpi
        val newComponentCustomizationData = """{"data":"new"}"""
        val newDataQuery = "new query"
        val newDataStore = UUID.randomUUID()
        every { producer.produce(any(), any()) } just runs

        workspaceService.addOrUpdateComponent(
            componentId.value,
            workspaceId,
            newComponentName,
            newDataQuery,
            newDataStore,
            newComponentType,
            newComponentCustomizationData,
            customProperties = emptyArray()
        )

        assertTrue {
            WorkspaceComponents.select {
                WorkspaceComponents.id eq componentId and
                        (WorkspaceComponents.name eq newComponentName) and
                        (WorkspaceComponents.query eq newDataQuery) and
                        (WorkspaceComponents.componentType eq ComponentTypeDto.CausalNet.typeName) and
                        (WorkspaceComponents.customizationData eq newComponentCustomizationData)
            }.any()
        }
    }

    @Test
    fun `skips workspace component field update if new value is null`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val oldComponentName = "oldName"
        val oldComponentType = ComponentTypeDto.Kpi
        val oldComponentCustomizationData = """{"data":"new"}"""
        val oldDataQuery = "query"
        val organizationId = createOrganization().id.value
        val userId = createUser().id.value
        val workspaceId = createWorkspace("Workspace1", userId, organizationId)
        val componentId = createWorkspaceComponent(
            oldComponentName,
            workspaceId,
            query = oldDataQuery,
            componentType = oldComponentType,
            customizationData = oldComponentCustomizationData
        )
        every { producer.produce(any(), any()) } just runs

        workspaceService.addOrUpdateComponent(
            componentId.value,
            workspaceId,
            name = null,
            query = null,
            dataStore = null,
            componentType = null,
            customizationData = null,
            customProperties = emptyArray()
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
    fun `creates workspace component if the component does not exist`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val componentId = UUID.randomUUID();
        val componentName = "oldName"
        val componentType = ComponentTypeDto.Kpi
        val componentCustomizationData = """{"data":"new"}"""
        val dataQuery = "query"
        val dataStore = UUID.randomUUID()
        val organizationId = createOrganization().id.value
        val userId = createUser().id.value
        val workspaceId = createWorkspace("Workspace1", userId, organizationId)
        every { producer.produce(any(), any()) } just runs

        workspaceService.addOrUpdateComponent(
            componentId,
            workspaceId,
            name = componentName,
            query = dataQuery,
            dataStore = dataStore,
            componentType = componentType,
            customizationData = componentCustomizationData,
            customProperties = emptyArray()
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

    @Test
    fun `removes workspace component if the component exists`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organizationId = createOrganization().id.value
        val userId = createUser().id.value
        val workspaceId = createWorkspace("Workspace1", userId, organizationId)
        val componentId = createWorkspaceComponent(workspaceId = workspaceId)
        every { producer.produce(any(), any()) } just runs

        workspaceService.removeComponent(componentId.value)

        assertTrue {
            WorkspaceComponent.find {
                WorkspaceComponents.id eq componentId
            }.all { it.deleted }
        }
    }

    @Test
    fun `component removal fails if the component does not exist`(): Unit = withCleanTables(
        AccessControlList, UsersInGroups, Users, Groups, Organizations,
        WorkspaceComponents, Workspaces
    ) {
        val organizationId = createOrganization().id.value
        val userId = createUser().id.value
        val workspaceId = createWorkspace("Workspace1", userId, organizationId)

        val exception =
            assertFailsWith<ValidationException>("The specified workspace component does not exist or the user has insufficient permissions to it") {
                workspaceService.removeComponent(UUID.randomUUID())
            }

        assertEquals(ExceptionReason.WorkspaceComponentNotFound, exception.reason)
    }
}
