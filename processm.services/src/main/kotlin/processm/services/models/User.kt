package processm.services.models

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable


object Users : LongIdTable("users") {
    val username = text("username")
    val password = text("password")
    val locale = varchar("locale", 5)
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users)

    var username by Users.username
    var password by Users.password
    var locale by Users.locale
    var organizations by Organization via UsersRolesInOrganizations
}