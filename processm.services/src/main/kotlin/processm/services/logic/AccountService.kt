package processm.services.logic

import com.kosprov.jargon2.api.Jargon2.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
import java.util.*

class AccountService {
    private val passwordHasher = jargon2Hasher()
        .type(Type.ARGON2d)
        .memoryCost(65536)
        .timeCost(3)
        .saltLength(16)
        .hashLength(16)
    private val passwordVerifier = jargon2Verifier()
    private val defaultLocale = Locale.UK

    fun verifyUsersCredentials(username: String, password: String) = transaction(DBConnectionPool.database) {
        val user = User.find(Op.build { Users.username eq username }).firstOrNull()
            ?: throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "Specified user account does not exist")

        if (verifyPassword(password, user.password)) user else null
    }

    fun createAccount(userEmail: String, organizationName: String, accountLocale: String? = null) {
        transaction(DBConnectionPool.database) {
            val organizationsCount = Organizations.select { Organizations.name eq organizationName }.limit(1).count()
            val usersCount = Users.select { Users.username eq userEmail }.limit(1).count()

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
                it[locale] = accountLocale ?: defaultLocale.toString()
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

    fun changeLocale(userId: Long, locale: String) = transaction(DBConnectionPool.database) {
        val user = getAccountDetails(userId)
        val localeObject = parseLocale(locale)

        user.locale = localeObject.toString()
    }

    private fun calculatePasswordHash(password: String) =
        passwordHasher.password(password.toByteArray()).encodedHash()

    private fun verifyPassword(password: String, passwordHash: String) =
        passwordVerifier.hash(passwordHash).password(password.toByteArray()).verifyEncoded()

    private fun parseLocale(locale: String): Locale {
        val localeTags = locale.split("_", "-")
        val localeObject = when (localeTags.size) {
            3 -> Locale(localeTags[0], localeTags[1], localeTags[2])
            2 -> Locale(localeTags[0], localeTags[1])
            1 -> Locale(localeTags[0])
            else -> throw ValidationException(
                ValidationException.Reason.ResourceFormatInvalid,
                "The provided locale string is in invalid format")
        }

        try {
            localeObject.getISO3Language()
            localeObject.getISO3Country()
        }
        catch (e: MissingResourceException) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The current locale could not be changed: ${e.message.orEmpty()}")
        }

        return localeObject
    }
}