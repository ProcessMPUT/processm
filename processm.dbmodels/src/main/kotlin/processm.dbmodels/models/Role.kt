package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.ieq
import java.util.*

object Roles : UUIDTable("roles") {
    val name = text("name")
}

class Role(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Role>(Roles)

    var name: RoleType by Roles.name.transform(RoleType::value, RoleType::byNameInDatabase)
}

enum class RoleType(val value: String) {
    // order is important
    Owner("owner"),
    Writer("writer"),
    Reader("reader"),
    None("none");

    companion object {
        fun byNameInDatabase(dbName: String) = values().first { it.value == dbName }
    }
}

/**
 * The database object representing this role.
 */
val RoleType.role: Role
    get() = transactionMain { Role.find { Roles.name ieq value }.first() }
