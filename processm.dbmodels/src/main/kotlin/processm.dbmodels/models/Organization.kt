package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
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
    var groups by Group via Groups
    val userRoles by UserRoleInOrganization referrersOn UsersRolesInOrganizations.organizationId
}

val Organization.sharedGroup: Group
    get() = Group.find { (Groups.organizationId eq id) and (Groups.isShared eq true) }.first()
