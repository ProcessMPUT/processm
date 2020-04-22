package processm.services.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Organizations : UUIDTable("organizations") {
    val name = text("name")
    val parentOrganizationId = long("parent_organization_id").nullable()
    val isPrivate = bool("is_private")
}

class Organization(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Organization>(Organizations)

    var name by Organizations.name
    var parentOrganizationId by Organizations.parentOrganizationId
    var isPrivate by Organizations.isPrivate
    var users by User via UsersRolesInOrganizations
}