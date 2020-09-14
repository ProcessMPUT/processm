package processm.dbmodels.models

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object UserGroupWithWorkspaces : IdTable<UUID>("user_groups_with_workspaces") {
    val userGroupId = reference("user_group_id", UserGroups)
    val workspaceId = reference("workspace_id", Workspaces)
    val organizationId = reference("organization_id", Organizations)
    override val primaryKey = PrimaryKey(
        userGroupId,
        workspaceId,
        organizationId
    )
    override val id: Column<EntityID<UUID>>
        get() = userGroupId
}

class UserGroupWithWorkspace(userGroupId: EntityID<UUID>) : Entity<UUID>(userGroupId) {
    companion object : EntityClass<UUID, UserGroupWithWorkspace>(
        UserGroupWithWorkspaces
    )

    val userGroup by UserGroup referencedOn UserGroupWithWorkspaces.userGroupId
    val workspace by Workspace referencedOn UserGroupWithWorkspaces.workspaceId
    val organization by Organization referencedOn UserGroupWithWorkspaces.organizationId

    fun toDto() = UserGroupWithWorkspaceDto(
        userGroup.toDto(),
        workspace.toDto(),
        organization.toDto()
    )
}

data class UserGroupWithWorkspaceDto(val userGroup: UserGroupDto, val workspace: WorkspaceDto, val organization: OrganizationDto)