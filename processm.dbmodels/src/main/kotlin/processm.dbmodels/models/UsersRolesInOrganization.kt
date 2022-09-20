package processm.dbmodels.models

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object UsersRolesInOrganizations : IdTable<UUID>("users_roles_in_organizations") {
    val userId = reference("user_id", Users)
    val organizationId = reference("organization_id", Organizations)
    val roleId = reference("organization_role_id", OrganizationRoles)
    override val primaryKey = PrimaryKey(userId, organizationId, roleId)
    override val id: Column<EntityID<UUID>>
        get() = userId
}

class UserRoleInOrganization(userId: EntityID<UUID>) : Entity<UUID>(userId) {
    companion object : EntityClass<UUID, UserRoleInOrganization>(UsersRolesInOrganizations)

    var user by User referencedOn UsersRolesInOrganizations.userId
    var organization by Organization referencedOn UsersRolesInOrganizations.organizationId
    var role by OrganizationRole referencedOn UsersRolesInOrganizations.roleId
}
