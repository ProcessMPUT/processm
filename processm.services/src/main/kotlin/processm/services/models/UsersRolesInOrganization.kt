package processm.services.models

import org.jetbrains.exposed.sql.Table

object UsersRolesInOrganizations : Table("users_roles_in_organizations") {
    val user = reference("user_id", Users)
    val organization = reference("organization_id", Organizations)
    val role = reference("organization_role_id", OrganizationRoles)
    override val primaryKey = PrimaryKey(user, organization, role)
}