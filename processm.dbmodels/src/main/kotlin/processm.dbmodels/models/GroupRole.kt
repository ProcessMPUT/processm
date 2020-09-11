package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import processm.dbmodels.ilike
import java.util.*

object GroupRoles : UUIDTable("group_roles") {
    val name = text("role")
}

class GroupRole(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<GroupRole>(GroupRoles)

    var name: GroupRoleDto by GroupRoles.name.transform({ it.roleName }, { GroupRoleDto.byNameInDatabase(it) })
}

fun GroupRoles.getIdByName(groupRole: GroupRoleDto): EntityID<UUID> {
    return GroupRole.find { name ilike groupRole.roleName }.first().id
}

enum class GroupRoleDto(val roleName: String) {
    Owner("owner"),
    Writer("writer"),
    Reader("reader");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = values().first { it.roleName == nameInDatabase }
    }
}