package processm.services.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Users : UUIDTable("users") {
    val username = text("username").nullable()
    val email = text("email_address")
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val password = text("password")
    val locale = text("locale")
}

class User(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<User>(Users)

    var username by Users.username
    var email by Users.email
    var firstName by Users.firstName
    var lastName by Users.lastName
    var password by Users.password
    var locale by Users.locale
    var organizations by Organization via UsersRolesInOrganizations
    // do not declare the following until exposed supports DAO with composite key
    // val rolesInOrganizations by UserRolesInOrganizations referrersOn UsersRolesInOrganizations.userId

    fun toDto() = UserDto(id.value, email, locale)
}

data class UserDto(val id: UUID, val email: String, val locale: String)