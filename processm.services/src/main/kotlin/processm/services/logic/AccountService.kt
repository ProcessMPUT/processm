package processm.services.logic

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.models.*

class AccountService {
    private val passwordHashingComplexity = 8

    fun verifyUsersCredentials(username: String, password: String) = transaction(DBConnectionPool.database) {
        val user = User.find(Op.build { Users.username eq username }).firstOrNull()
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Specified user account does not exist"
            )

        BCrypt.verifyer().verify(password.toByteArray(), user.password.toByteArray()).verified
    }

    fun createAccount(userEmail: String, organizationName: String) {
        transaction(DBConnectionPool.database) {
            val organizationsCount = Organization.count( Op.build { Organizations.name eq organizationName })
            val usersCount = User.count(Op.build { Users.username eq userEmail })

            if (usersCount > 0 || organizationsCount > 0) {
                throw ValidationException(
                    ValidationException.Reason.ResourceAlreadyExists,
                    "User and/or organization with specified name already exists")
            }

            //TODO: registered accounts should be stored as "pending' until confirmed
            // user password should be specified upon successful confirmation

            val userId = Users.insertAndGetId {
                it[username] = userEmail
                it[password] = BCrypt.withDefaults().hashToString(passwordHashingComplexity, "pass".toCharArray())
            }

            val organizationId = Organizations.insertAndGetId {
                it[name] = organizationName
                it[isPrivate] = false
            }

            UsersRolesInOrganizations.insert {
                it[user] = userId
                it[organization] = organizationId
                it[role] = OrganizationRoles.getIdByName(OrganizationRole.Owner)
            }
        }
    }
}