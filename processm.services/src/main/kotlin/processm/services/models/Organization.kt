package processm.services.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Organizations : UUIDTable("organizations") {
    val name = text("name")
    val parentOrganizationId = reference("parent_organization_id", Organizations).nullable()
    val isPrivate = bool("is_private")
}

class Organization(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Organization>(Organizations)

    var name by Organizations.name
    var parentOrganization by Organization optionalReferencedOn Organizations.parentOrganizationId
    var isPrivate by Organizations.isPrivate
    var users by User via UsersRolesInOrganizations
    val userGroups by UserGroup referrersOn UserGroups.organizationId
    val userRoles by UserRolesInOrganizations referrersOn UsersRolesInOrganizations.organizationId

    fun toDto() = OrganizationDto(id.value, name, isPrivate)
}

data class OrganizationDto(val id: UUID, val name: String, val isPrivate: Boolean)