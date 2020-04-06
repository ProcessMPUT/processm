package processm.services.models

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.select

object OrganizationRoles : LongIdTable("organization_roles") {
    val name = text("name")
}

fun OrganizationRoles.getIdByName(organizationRole: OrganizationRole) : EntityID<Long> {
    return OrganizationRoles.select { name eq organizationRole.name.toLowerCase() }.map { it[id] }.first()
}

enum class OrganizationRole {
    Owner,
    Writer,
    Reader
}