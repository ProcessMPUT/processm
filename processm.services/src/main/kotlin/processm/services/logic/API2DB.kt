package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.ieq
import processm.dbmodels.models.*
import java.util.*

typealias ApiOrganization = processm.services.api.models.Organization
typealias ApiRole = processm.services.api.models.OrganizationRole
typealias ApiOrganizationMember = processm.services.api.models.OrganizationMember
typealias ApiWorkspace = processm.services.api.models.Workspace
typealias ApiGroup = processm.services.api.models.Group
typealias ApiUserRoleInOrganization = processm.services.api.models.UserRoleInOrganization
typealias ApiEntityID = processm.services.api.models.EntityID
typealias ApiEntityType = processm.services.api.models.EntityType


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
    isPrivate = isPrivate,
    parentOrganizationId = parentOrganization?.id?.value
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
    isShared = isShared,
    organizationName = organizationId?.name
)

fun UserRoleInOrganization.toApi() = ApiUserRoleInOrganization(
    organization = organization.toApi(),
    role = role.toApi()
)

fun EntityID<UUID>.toApi(): ApiEntityID = when (table) {
    is Workspaces ->
        ApiEntityID(ApiEntityType.workspace, value, Workspace.findById(value)?.name)

    is DataStores ->
        ApiEntityID(ApiEntityType.dataStore, value, DataStore.findById(value)?.name)

    else -> error("Unsupported table `${table.tableName}`")
}
