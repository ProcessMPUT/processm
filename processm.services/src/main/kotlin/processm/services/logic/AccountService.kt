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
    private val defaultLocale = "en-US"

    fun verifyUsersCredentials(username: String, password: String) = transaction(DBConnectionPool.database) {
        val user = User.find(Op.build { Users.username eq username }).firstOrNull()
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Specified user account does not exist")

        if (verifyPassword(password, user.password)) user else null
    }

    fun createAccount(userEmail: String, organizationName: String, accountLocale: String? = null) {
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
                it[password] = calculatePasswordHash("pass")
                it[locale] = accountLocale ?: defaultLocale
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

    fun getAccountDetails(userId: Long) = transaction(DBConnectionPool.database) {
        User.findById(userId)
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Specified user account does not exist")
    }

    fun changePassword(userId: Long, currentPassword: String, newPassword: String) = transaction(DBConnectionPool.database) {
        val user = getAccountDetails(userId)

        if (!verifyPassword(currentPassword, user.password)) {
            return@transaction false
        }

        user.password = calculatePasswordHash(newPassword)

        return@transaction true
    }

    private fun calculatePasswordHash(password: String) =
        BCrypt.withDefaults().hashToString(passwordHashingComplexity, password.toCharArray())

    private fun verifyPassword(password: String, passwordHash: String) =
        BCrypt.verifyer().verify(password.toByteArray(), passwordHash.toByteArray()).verified
}