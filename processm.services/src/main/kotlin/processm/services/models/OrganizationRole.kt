package processm.services.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import processm.services.ilike
import java.util.*

object OrganizationRoles : UUIDTable("organization_roles") {
    val name = text("name")
}

class OrganizationRole(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OrganizationRole>(OrganizationRoles)

    var name: OrganizationRoleDto by OrganizationRoles.name.transform({it.roleName}, {OrganizationRoleDto.byNameInDatabase(it)})
}

fun OrganizationRoles.getIdByName(organizationRole: OrganizationRoleDto): EntityID<UUID> {
    return OrganizationRole.find { name ilike organizationRole.roleName }.first().id
}

enum class OrganizationRoleDto(val roleName: String) {
    Owner("owner"),
    Writer("writer"),
    Reader("reader");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = values().first {it.roleName == nameInDatabase }
    }
}