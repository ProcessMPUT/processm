package processm.services.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object OrganizationRoles : UUIDTable("organization_roles") {
    val name = text("name")
}

class OrganizationRole(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OrganizationRole>(OrganizationRoles)

    var name: OrganizationRoleDto by OrganizationRoles.name.transform({it.roleName}, {OrganizationRoleDto.byNameInDatabase(it)})
}

fun OrganizationRoles.getIdByName(organizationRole: OrganizationRoleDto): EntityID<UUID> {
    return OrganizationRole.find { name eq organizationRole.roleName }.first().id //OrganizationRoles.select { name. eq organizationRole.nameInDatabase }.map { it[id] }.first()
}

enum class OrganizationRoleDto(val id: UUID, val roleName: String) {
    Owner(UUID.fromString("b8cd11db-b386-4f1d-bd94-f491e591dc20"),"owner"),
    Writer(UUID.fromString("c26bc1ec-946d-4975-8651-a763a9609b7e"),"writer"),
    Reader(UUID.fromString("5939d0fe-2c7e-4e0d-9469-a14dc0db4db0"),"reader");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = OrganizationRoleDto.values().first {it.roleName == nameInDatabase }
    }
}