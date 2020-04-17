package processm.services.models

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object Organizations: LongIdTable("organizations") {
    val name = text("name")
    val parentOrganizationId = long("parent_organization_id").nullable()
    val isPrivate = bool("is_private")
}

class Organization(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<Organization>(Organizations)

    var name by Organizations.name
    var parentOrganizationId by Organizations.parentOrganizationId
    var isPrivate by Organizations.isPrivate
    var users by User via UsersRolesInOrganizations
}