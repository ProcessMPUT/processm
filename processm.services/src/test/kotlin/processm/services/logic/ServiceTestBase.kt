package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.*
import java.time.LocalDateTime
import java.util.*

abstract class ServiceTestBase {
    protected fun <R> withCleanTables(vararg tables: Table, testLogic: Transaction.() -> R) =
        transaction(DBCache.getMainDBPool().database) {
            tables.forEach { it.deleteAll() }
            testLogic(this)
        }

    protected fun Transaction.createUser(
        userEmail: String = "user@example.com",
        passwordHash: String = "###",
        locale: String = "en_US",
        privateGroupId: UUID? = null
    ): EntityID<UUID> {
        val groupId = privateGroupId ?: createGroup(groupRole = GroupRoleDto.Owner, isImplicit = true).value

        val userId = Users.insertAndGetId {
            it[email] = userEmail
            it[password] = passwordHash
            it[Users.locale] = locale
            it[Users.privateGroupId] = EntityID(groupId, UserGroups)
        }

        attachUserToGroup(userId.value, groupId)

        return userId
    }

    protected fun Transaction.createOrganization(
        name: String = "Org1",
        isPrivate: Boolean = false,
        parentOrganizationId: UUID? = null,
        sharedGroupId: UUID? = null
    ): EntityID<UUID> {
        val groupId = sharedGroupId ?: createGroup(groupRole = GroupRoleDto.Reader, isImplicit = true).value

        return Organizations.insertAndGetId {
            it[this.name] = name
            it[this.isPrivate] = isPrivate
            it[this.parentOrganizationId] =
                if (parentOrganizationId != null) EntityID(parentOrganizationId, Organizations) else null
            it[this.sharedGroupId] = EntityID(groupId, UserGroups)
        }
    }

    protected fun createDataSource(
        organizationId: UUID,
        name: String = "DataSource#1",
        creationDate: LocalDateTime = LocalDateTime.now()
    ): EntityID<UUID> {
        return DataSources.insertAndGetId {
            it[DataSources.name] = name
            it[DataSources.creationDate] = creationDate
            it[DataSources.organizationId] = EntityID(organizationId, Organizations)
        }
    }

    protected fun Transaction.attachUserToOrganization(
        userId: UUID,
        organizationId: UUID,
        organizationRole: OrganizationRoleDto = OrganizationRoleDto.Reader
    ) =
        UsersRolesInOrganizations.insertAndGetId {
            it[UsersRolesInOrganizations.userId] = EntityID(userId, Users)
            it[UsersRolesInOrganizations.organizationId] = EntityID(organizationId, Organizations)
            it[roleId] = OrganizationRoles.getIdByName(organizationRole)
        }

    protected fun Transaction.createGroup(
        name: String = "Group1",
        parentGroupId: UUID? = null,
        groupRole: GroupRoleDto = GroupRoleDto.Reader,
        isImplicit: Boolean = false
    ) =
        UserGroups.insertAndGetId {
            it[this.name] = name
            it[this.parentGroupId] = if (parentGroupId != null) EntityID(parentGroupId, UserGroups) else null
            it[this.groupRoleId] = GroupRoles.getIdByName(groupRole)
            it[this.isImplicit] = isImplicit
        }

    protected fun Transaction.attachUserToGroup(userId: UUID, groupId: UUID) =
        UsersInGroups.insert {
            it[this.userId] = EntityID(userId, Users)
            it[this.groupId] = EntityID(groupId, UserGroups)
        }

    protected fun Transaction.createWorkspace(name: String = "Workspace1") =
        Workspaces.insertAndGetId {
            it[this.name] = name
        }

    protected fun Transaction.attachUserGroupToWorkspace(
        userGroupId: UUID,
        workspaceId: UUID,
        organizationId: UUID? = null
    ) =
        UserGroupWithWorkspaces.insertAndGetId {
            it[this.userGroupId] = EntityID(userGroupId, UserGroups)
            it[this.workspaceId] = EntityID(workspaceId, Workspaces)
            it[this.organizationId] = EntityID(organizationId ?: createOrganization().value, Organizations)
        }

    protected fun Transaction.createWorkspaceComponent(
        name: String = "Component1",
        componentWorkspaceId: UUID? = null,
        query: String = "SELECT ...",
        componentType: ComponentTypeDto = ComponentTypeDto.CausalNet,
        dataSourceId: Int = 0,
        customizationData: String = "{}"
    ): EntityID<UUID> {
        val workspaceId = componentWorkspaceId ?: createWorkspace().value

        return WorkspaceComponents.insertAndGetId {
            it[this.name] = name
            it[this.workspaceId] = EntityID(workspaceId, Workspaces)
            it[this.query] = query
            it[this.componentType] = componentType.typeName
            it[componentDataSourceId] = dataSourceId
            it[this.customizationData] = customizationData
        }
    }
}
