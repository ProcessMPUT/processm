package processm.services.logic

import com.kosprov.jargon2.api.Jargon2.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import processm.services.ilike
import processm.services.models.*
import java.util.*

class AccountService(private val groupService: GroupService) {
    private val passwordHasher =
        jargon2Hasher().type(Type.ARGON2d).memoryCost(65536).timeCost(3).saltLength(16).hashLength(16)
    private val passwordVerifier = jargon2Verifier()
    private val defaultLocale = Locale.UK

    fun verifyUsersCredentials(username: String, password: String) = transaction(DBConnectionPool.database) {
        val user = User.find(Users.email ilike username).firstOrNull() ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "Specified user account does not exist"
        )

        if (verifyPassword(password, user.password)) user.toDto() else null
    }

    fun createAccount(userEmail: String, organizationName: String, accountLocale: String? = null) {
        transaction(DBConnectionPool.database) {
            val organizationsCount = Organizations.select { Organizations.name eq organizationName }.limit(1).count()
            val usersCount = Users.select { Users.email ilike userEmail }.limit(1).count()

            if (usersCount > 0 || organizationsCount > 0) {
                throw ValidationException(
                    ValidationException.Reason.ResourceAlreadyExists,
                    "User and/or organization with specified email already exists"
                )
            }
            //TODO: registered accounts should be stored as "pending' until confirmed
            // user password should be specified upon successful confirmation
            // user creation should be moved to a separate method

            // automatically created group for the particular user
            val privateGroupId = UserGroups.insertAndGetId {
                it[groupRoleId] = GroupRoles.getIdByName(GroupRoleDto.Owner)
                it[isImplicit] = true
            }
            // automatically created group for all users
            val sharedGroupId = UserGroups.insertAndGetId {
                it[groupRoleId] = GroupRoles.getIdByName(GroupRoleDto.Reader)
                it[isImplicit] = true
            }
            val organizationId = Organizations.insertAndGetId {
                it[name] = organizationName
                it[isPrivate] = false
                it[this.sharedGroupId] = sharedGroupId
            }
            val userId = Users.insertAndGetId {
                it[email] = userEmail
                it[password] = calculatePasswordHash("pass")
                it[locale] = accountLocale ?: defaultLocale.toString()
                it[this.privateGroupId] = privateGroupId
            }

            groupService.attachUserToGroup(userId.value, sharedGroupId.value)
            groupService.attachUserToGroup(userId.value, privateGroupId.value)
            UsersRolesInOrganizations.insert {
                it[this.userId] = userId
                it[this.organizationId] = organizationId
                it[roleId] = OrganizationRoles.getIdByName(OrganizationRoleDto.Owner)
            }
        }
    }

    fun getAccountDetails(userId: UUID) = transaction(DBConnectionPool.database) {
        getUserDao(userId).toDto()
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) =
        transaction(DBConnectionPool.database) {
            val user = getUserDao(userId)

            if (!verifyPassword(currentPassword, user.password)) {
                return@transaction false
            }

            user.password = calculatePasswordHash(newPassword)

            return@transaction true
        }

    fun changeLocale(userId: UUID, locale: String) = transaction(DBConnectionPool.database) {
        val user = getUserDao(userId)
        val localeObject = parseLocale(locale)

        user.locale = localeObject.toString()
    }

    fun getRolesAssignedToUser(userId: UUID) = transaction(DBConnectionPool.database) {
        // This returns only organizations explicitly assigned to the user account.
        // Inferring the complete set of user roles (including inherited roles) is expensive
        // so its probably faster to check the appropriate roles on case by case basis
        // e.g. with getInheritedRoles(userId, organizationId) method.
        val user = getUserDao(userId).toDto()

        // The following implementation purposefully does not use back-referencing UserRolesInOrganizations with specified userId.
        // Exposed does not support DAOs with composite keys, hence only one column can be marked as the primary key.
        // In case of UserRolesInOrganizations the column marked as primary key is userId,
        // this would cause a collection of all organizations related to the same user to be a collection of DAOs
        // with the same ID (userId) and that is incorrect - exposed represents it as a collection of the same objects.
        UsersRolesInOrganizations
            .innerJoin(Organizations)
            .innerJoin(OrganizationRoles)
            .select {
                UsersRolesInOrganizations.userId eq userId }
            .map {
                OrganizationMemberDto(user, Organization.wrapRow(it).toDto(), OrganizationRole.wrapRow(it).name) }
    }

    private fun getUserDao(userId: UUID) = transaction(DBConnectionPool.database) {
        User.findById(userId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "Specified user account does not exist"
        )
    }

    private fun calculatePasswordHash(password: String) = passwordHasher.password(password.toByteArray()).encodedHash()

    private fun verifyPassword(password: String, passwordHash: String) =
        passwordVerifier.hash(passwordHash).password(password.toByteArray()).verifyEncoded()

    private fun parseLocale(locale: String): Locale {
        val localeTags = locale.split("_", "-")
        val localeObject = when (localeTags.size) {
            3 -> Locale(localeTags[0], localeTags[1], localeTags[2])
            2 -> Locale(localeTags[0], localeTags[1])
            1 -> Locale(localeTags[0])
            else -> throw ValidationException(
                ValidationException.Reason.ResourceFormatInvalid, "The provided locale string is in invalid format"
            )
        }

        try {
            localeObject.isO3Language
            localeObject.isO3Country
        } catch (e: MissingResourceException) {
            throw ValidationException(
                ValidationException.Reason.ResourceNotFound,
                "The current locale could not be changed: ${e.message.orEmpty()}"
            )
        }

        return localeObject
    }
}