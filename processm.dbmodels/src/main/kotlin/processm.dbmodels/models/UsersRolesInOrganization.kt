package processm.dbmodels.models

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object UsersRolesInOrganizations : UUIDTable("users_roles_in_organizations") {
    val userId = reference("user_id", Users)
    val organizationId = reference("organization_id", Organizations)
    val roleId = reference("organization_role_id", Roles)
}

class UserRoleInOrganization(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : EntityClass<UUID, UserRoleInOrganization>(UsersRolesInOrganizations)

    var user by User referencedOn UsersRolesInOrganizations.userId
    var organization by Organization referencedOn UsersRolesInOrganizations.organizationId
    var role by Role referencedOn UsersRolesInOrganizations.roleId
}
