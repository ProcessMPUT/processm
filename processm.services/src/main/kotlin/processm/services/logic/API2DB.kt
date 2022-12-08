package processm.services.logic

import processm.core.persistence.connection.transactionMain
import processm.dbmodels.ieq
import processm.dbmodels.models.*

typealias ApiOrganization = processm.services.api.models.Organization
typealias ApiRole = processm.services.api.models.OrganizationRole
typealias ApiOrganizationMember = processm.services.api.models.OrganizationMember
typealias ApiWorkspace = processm.services.api.models.Workspace
typealias ApiGroup = processm.services.api.models.Group

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

fun Group.toApi() = ApiGroup(
    id = id.value,
    name = name ?: "(no name)",
    organizationId = organizationId?.id?.value,
    isImplicit = isImplicit,
    isShared = isShared
)

