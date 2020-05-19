package processm.services.models

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

class UserRolesInOrganizations(userId: EntityID<UUID>) : Entity<UUID>(userId) {
    companion object : EntityClass<UUID, UserRolesInOrganizations>(UsersRolesInOrganizations)

    val user by User referencedOn UsersRolesInOrganizations.userId
    val organization by Organization referencedOn UsersRolesInOrganizations.organizationId
    val role by OrganizationRole referencedOn UsersRolesInOrganizations.roleId

    fun toDto() = OrganizationMemberDto(user.toDto(), organization.toDto(), role.name)
}

data class OrganizationMemberDto(val user: UserDto, val organization: OrganizationDto, val role: OrganizationRoleDto)