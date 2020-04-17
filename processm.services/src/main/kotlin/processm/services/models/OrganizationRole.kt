package processm.services.models

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.select

object OrganizationRoles: LongIdTable("organization_roles") {
    val name = text("name")
}

fun OrganizationRoles.getIdByName(organizationRole: OrganizationRole): EntityID<Long> {
    return OrganizationRoles.select { name eq organizationRole.nameInDatabase }.map { it[id] }.first()
}

enum class OrganizationRole(val nameInDatabase: String) {
    Owner("owner"),
    Writer("writer"),
    Reader("reader")
}