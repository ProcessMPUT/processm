package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Users : UUIDTable("users") {
    val email = text("email_address")
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val password = text("password")
    val locale = text("locale")

    /**
     * A single-member group for storing private workspaces.
     */
    val privateGroupId = reference("private_group_id", Groups)
}

class User(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<User>(Users)

    var email by Users.email
    var firstName by Users.firstName
    var lastName by Users.lastName
    var password by Users.password
    var locale by Users.locale
    var organizations by Organization via UsersRolesInOrganizations
    val rolesInOrganizations by UserRoleInOrganization referrersOn UsersRolesInOrganizations.userId
    var privateGroup by Group referencedOn Users.privateGroupId
}


