package processm.services.logic

import com.kosprov.jargon2.api.Jargon2.*
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.logging.loggedScope
import processm.core.persistence.connection.DBCache
import processm.dbmodels.ilike
import processm.dbmodels.models.*
import java.util.*

class AccountService(private val groupService: GroupService) {
    private val passwordHasher =
        jargon2Hasher().type(Type.ARGON2d).memoryCost(65536).timeCost(3).saltLength(16).hashLength(16)
    private val passwordVerifier = jargon2Verifier()
    private val defaultLocale = Locale.UK

    /**
     * Verifies that [username] with the specified [password] exists and returns the [UserDto] object.
     * Throws [ValidationException] if the specified [username] doesn't exist.
     */
    fun verifyUsersCredentials(username: String, password: String) =
        loggedScope { logger ->
            transaction(DBCache.getMainDBPool().database) {
                val user = User.find(Users.email ilike username).firstOrNull()

                if (user == null) {
                    logger.debug("The specified username ${username} is unknown and cannot be verified")
                    throw ValidationException(
                        ValidationException.Reason.ResourceNotFound, "The specified user account does not exist"
                    )
                }

                return@transaction if (verifyPassword(password, user.password)) user.toDto() else null
            }
        }

    /**
     * Creates new account
     */
    fun createUser(
        userEmail: String,
        accountLocale: String? = null,
        pass: String
    ): User = loggedScope { logger ->
        transaction(DBCache.getMainDBPool().database) {
            val usersCount = Users.select { Users.email ilike userEmail }.limit(1).count()
            if (usersCount > 0) {
                throw ValidationException(
                    ValidationException.Reason.ResourceAlreadyExists,
                    "The user with the given email already exists."
                )
            }

            // automatically created group for the particular user
            val privateGroup = UserGroup.new {
                groupRole = GroupRoleType.Owner.groupRole
                isImplicit = true
            }

            val user = User.new {
                this.email = userEmail
                this.password = calculatePasswordHash(pass)
                this.locale = accountLocale ?: defaultLocale.toString()
                this.privateGroup = privateGroup
            }

            groupService.attachUserToGroup(user.id.value, privateGroup.id.value)

            user
        }
    }

    /**
     * Returns [UserDto] object for the user with the specified [userId].
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun getAccountDetails(userId: UUID) = transaction(DBCache.getMainDBPool().database) {
        getUserDao(userId).toDto()
    }

    /**
     * Changes user's [currentPassword] to [newPassword] for the user with the specified [userId] and returns true if the operation succeeds or false otherwise.
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) =
        loggedScope { logger ->
            transaction(DBCache.getMainDBPool().database) {
                val user = getUserDao(userId)

                if (!verifyPassword(currentPassword, user.password)) {
                    logger.debug("A user password cannot be changed for user $userId due to an invalid current password")
                    return@transaction false
                }

                user.password = calculatePasswordHash(newPassword)
                logger.debug("A user password has been successfully changed for the user $userId")

                return@transaction true
            }
        }

    /**
     * Changes user's [locale] settings for the user with the specified [userId].
     * Throws [ValidationException] if the specified [userId] doesn't exist or the [locale] cannot be parsed.
     */
    fun changeLocale(userId: UUID, locale: String) = transaction(DBCache.getMainDBPool().database) {
        val user = getUserDao(userId)
        val localeObject = parseLocale(locale)

        user.locale = localeObject.toString()
    }

    /**
     * Returns a collection of all user's roles assigned to the organizations the user with the specified [userId] is member of.
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun getRolesAssignedToUser(userId: UUID) = transaction(DBCache.getMainDBPool().database) {
        // This returns only organizations explicitly assigned to the user account.
        // Inferring the complete set of user roles (including inherited roles) is expensive
        // so its probably faster to check the appropriate roles on case by case basis
        // e.g. with getInheritedRoles(userId, organizationId) method.
        val user = getUserDao(userId).toDto()

        // The following implementation purposefully does not use back-referencing UserRoleInOrganization with specified userId.
        // Exposed does not support DAOs with composite keys, hence only one column can be marked as the primary key.
        // In case of UserRoleInOrganization the column marked as primary key is userId,
        // this would cause a collection of all organizations related to the same user to be a collection of DAOs
        // with the same ID (userId) and that is incorrect - exposed represents it as a collection of the same objects.
        UsersRolesInOrganizations
            .innerJoin(Organizations)
            .innerJoin(OrganizationRoles)
            .select {
                UsersRolesInOrganizations.userId eq userId
            }
            .map {
                OrganizationMemberDto(user, Organization.wrapRow(it).toDto(), OrganizationRole.wrapRow(it).toApi())
            }
    }

    /**
     * Gets all users within the organizations associated with the [queryingUserId] (i.e., for security reasons, it does not
     * return users from other organizations).
     */
    fun getUsers(queryingUserId: UUID, emailFilter: String? = null, limit: Int = 10) =
        transaction(DBCache.getMainDBPool().database) {
            val URIO = UsersRolesInOrganizations
            val urio1 = URIO.alias("urio1")
            val urio2 = URIO.alias("urio2")
            urio1
                .join(urio2, JoinType.INNER, urio1[URIO.organizationId], urio2[URIO.organizationId])
                .join(Users, JoinType.INNER, urio2[URIO.userId], Users.id)
                .select { urio1[URIO.userId] eq queryingUserId }
                .andWhere { Users.email ilike "%${emailFilter}%" }
                .withDistinct()
                .limit(limit)
                .map { User.wrapRow(it).toDto() }
        }

    private fun getUserDao(userId: UUID) = transaction(DBCache.getMainDBPool().database) {
        User.findById(userId) ?: throw ValidationException(
            ValidationException.Reason.ResourceNotFound, "The specified user account does not exist"
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
