package processm.services.logic

import processm.core.persistence.connection.transactionMain
import processm.dbmodels.ieq
import processm.dbmodels.models.*
import java.util.*

typealias ApiOrganization = processm.services.api.models.Organization
typealias ApiRole = processm.services.api.models.OrganizationRole
typealias ApiOrganizationMember = processm.services.api.models.OrganizationMember
typealias ApiWorkspace = processm.services.api.models.Workspace

fun Role.toApi(): ApiRole = when (this.name) {
    RoleType.Owner -> ApiRole.owner
    RoleType.Writer -> ApiRole.writer
    RoleType.Reader -> ApiRole.reader
    RoleType.None -> ApiRole.none
}

fun ApiRole.toDB(): Role =
    transactionMain { Role.find { Roles.name ieq this@toDB.name }.first() }

fun ApiRole.toRoleType(): RoleType = when (this) {
    ApiRole.reader -> RoleType.Reader
    ApiRole.writer -> RoleType.Writer
    ApiRole.owner -> RoleType.Owner
    ApiRole.none -> RoleType.None
}

fun Organization.toApi(): ApiOrganization = ApiOrganization(
    id = id.value, // the order of constructor arguments tends to change in the generated classes, thus we pass arguments by name
    name = name,
    isPrivate = isPrivate
)

fun Workspace.toApi(): ApiWorkspace = ApiWorkspace(
    id = id.value, // the order of constructor arguments tends to change in the generated classes, thus we pass arguments by name
    name = name
)

// TODO: drop these nonsense-layer classes below:

data class UserDto(val id: UUID, val email: String, val locale: String, val privateGroup: UserGroupDto)

fun User.toDto() = UserDto(id.value, email, locale, privateGroup.toDto())

data class UserGroupDto(val id: UUID, val name: String?, val isImplicit: Boolean)

fun Group.toDto() = UserGroupDto(id.value, name, isImplicit)

data class GroupMemberDto(val user: UserDto, val group: UserGroupDto)

fun UsersInGroup.toDto() = GroupMemberDto(user.toDto(), group.toDto())
