package processm.services.logic

import processm.dbmodels.ilike
import processm.dbmodels.models.*
import java.util.*

typealias ApiGroupRole = processm.services.api.models.GroupRole
typealias ApiOrganizationRole = processm.services.api.models.OrganizationRole
typealias ApiOrganizationMember = processm.services.api.models.OrganizationMember

fun OrganizationRole.toApi(): ApiOrganizationRole = when (this.name) {
    OrganizationRoleType.Owner -> ApiOrganizationRole.owner
    OrganizationRoleType.Writer -> ApiOrganizationRole.writer
    OrganizationRoleType.Reader -> ApiOrganizationRole.reader
}

fun ApiOrganizationRole.toDB(): OrganizationRole =
    OrganizationRole.find { OrganizationRoles.name ilike this@toDB.name }.first()

fun GroupRole.toApi(): ApiGroupRole = when (this.name) {
    GroupRoleType.Owner -> ApiGroupRole.owner
    GroupRoleType.Writer -> ApiGroupRole.writer
    GroupRoleType.Reader -> ApiGroupRole.reader
}

fun ApiGroupRole.toDB(): GroupRole =
    GroupRole.find { GroupRoles.name ilike this@toDB.name }.first()

// TODO: drop these nonsense-layer classes below:
data class OrganizationMemberDto(val user: UserDto, val organization: OrganizationDto, val role: ApiOrganizationRole)

fun UserRoleInOrganization.toDto() = OrganizationMemberDto(user.toDto(), organization.toDto(), role.toApi())

data class UserDto(val id: UUID, val email: String, val locale: String, val privateGroup: UserGroupDto)

fun User.toDto() = UserDto(id.value, email, locale, privateGroup.toDto())

data class UserGroupDto(val id: UUID, val name: String?, val isImplicit: Boolean)

fun UserGroup.toDto() = UserGroupDto(id.value, name, isImplicit)

data class UserGroupWithWorkspaceDto(
    val userGroup: UserGroupDto,
    val workspace: WorkspaceDto,
    val organization: OrganizationDto
)

fun UserGroupWithWorkspace.toDto() = UserGroupWithWorkspaceDto(
    userGroup.toDto(),
    workspace.toDto(),
    organization.toDto()
)

data class GroupMemberDto(val user: UserDto, val group: UserGroupDto)

fun UsersInGroup.toDto() = GroupMemberDto(user.toDto(), group.toDto())

data class WorkspaceDto(val id: UUID, val name: String)

fun Workspace.toDto() = WorkspaceDto(id.value, name)
