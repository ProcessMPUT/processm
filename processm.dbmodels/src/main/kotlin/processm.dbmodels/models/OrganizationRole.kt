package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import processm.dbmodels.ilike
import java.util.*

object OrganizationRoles : UUIDTable("organization_roles") {
    val name = text("name")
}

class OrganizationRole(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OrganizationRole>(OrganizationRoles)

    var name: OrganizationRoleType by
    OrganizationRoles.name.transform(OrganizationRoleType::value, OrganizationRoleType::byNameInDatabase)
}

typealias OrganizationRoleType = GroupRoleType

/**
 * The database object representing this role.
 */
val OrganizationRoleType.organizationRole: OrganizationRole
    get() = OrganizationRole.find { OrganizationRoles.name ilike value }.first()
