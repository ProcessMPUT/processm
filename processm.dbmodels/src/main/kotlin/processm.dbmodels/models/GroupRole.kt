package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import processm.dbmodels.ieq
import java.util.*

object GroupRoles : UUIDTable("group_roles") {
    val name = text("role")
}

class GroupRole(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<GroupRole>(GroupRoles)

    var name: GroupRoleType by GroupRoles.name.transform(GroupRoleType::value, GroupRoleType::byNameInDatabase)
}

enum class GroupRoleType(val value: String) {
    Owner("owner"),
    Writer("writer"),
    Reader("reader");

    companion object {
        fun byNameInDatabase(nameInDatabase: String) = values().first { it.value == nameInDatabase }
    }
}

/**
 * The database object representing this role.
 */
val GroupRoleType.groupRole: GroupRole
    get() = GroupRole.find { GroupRoles.name ieq value }.first()
