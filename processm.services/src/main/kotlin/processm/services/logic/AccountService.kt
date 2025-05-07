package processm.services.logic

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import processm.core.communication.Producer
import processm.core.communication.email.EMAIL_ID
import processm.core.communication.email.EMAIL_TOPIC
import processm.core.communication.email.Email
import processm.core.communication.email.fromMessage
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.ieq
import processm.dbmodels.ilike
import processm.dbmodels.models.*
import processm.helpers.getPropertyIgnoreCase
import processm.logging.loggedScope
import processm.services.helpers.ExceptionReason
import processm.services.helpers.Patterns
import processm.services.helpers.isSupported
import java.time.Duration
import java.time.Instant
import java.util.*


class AccountService(private val groupService: GroupService, private val producer: Producer) {
    private val hasher = Argon2PasswordEncoder(16, 16, 1, 65536, 3)
    private val defaultLocale = Locale.US

    /**
     * Verifies that [username] with the specified [password] exists and returns the [UserDto] object.
     * Throws [ValidationException] if the specified [username] doesn't exist.
     */
    fun verifyUsersCredentials(username: String, password: String): User? =
        loggedScope { logger ->
            transactionMain {
                val user = User.find(Users.email ieq username).firstOrNull()
                    .validateNotNull(ExceptionReason.UserNotFound)

                return@transactionMain if (verifyPassword(password, user.password)) user else null
            }
        }

    /**
     * Creates new account
     */
    fun create(
        email: String,
        accountLocale: String? = null,
        pass: String
    ): User = loggedScope { logger ->
        transactionMain {
            Patterns.email.matches(email).validate(ExceptionReason.InvalidEmail, email)

            Patterns.password.matches(pass).validate(ExceptionReason.PasswordTooWeak)

            Users.select { Users.email ieq email }.limit(1).count().validate(0L, ExceptionReason.UserAlreadyExists)

            // automatically created group for the particular user // name group after username
            val privateGroup = groupService.create(email, organizationId = null)

            val user = User.new {
                this.email = email
                this.password = calculatePasswordHash(pass)
                this.locale = accountLocale ?: defaultLocale.toLanguageTag()
                this.privateGroup = privateGroup
            }

            groupService.attachUserToGroup(user.id.value, privateGroup.id.value)

            user
        }
    }

    /**
     * Changes user's [currentPassword] to [newPassword] for the user with the specified [userId] and returns true if the operation succeeds or false otherwise.
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) =
        loggedScope { logger ->
            transactionMain {
                val user = getUser(userId)

                if (!verifyPassword(currentPassword, user.password)) {
                    logger.debug("A user password cannot be changed for user $userId due to an invalid current password")
                    return@transactionMain false
                }

                user.password = calculatePasswordHash(newPassword)
                logger.debug("A user password has been successfully changed for the user $userId")

                return@transactionMain true
            }
        }

    /**
     * Changes user's [locale] settings for the user with the specified [userId].
     * Throws [ValidationException] if the specified [userId] doesn't exist or the [locale] cannot be parsed.
     */
    fun changeLocale(userId: UUID, locale: String) = update(userId) {
        val localeObject = Locale.forLanguageTag(locale)
        val languageTag = localeObject.toLanguageTag()
        if (languageTag != locale)
            throw ValidationException(ExceptionReason.InvalidLocale)
        if (!isSupported(localeObject))
            throw ValidationException(ExceptionReason.CannotChangeLocale)
        this.locale = languageTag
    }

    fun update(userId: UUID, update: (User.() -> Unit)): Unit = transactionMain {
        val user = getUser(userId)
        user.update()
    }

    /**
     * Deletes a user completely from the system. To detach a user from an organization, user [OrganizationService.removeMember].
     * @throws ValidationException if the user is not found.
     */
    fun remove(userId: UUID): Unit = transactionMain {
        Users.deleteWhere {
            Users.id eq userId
        }.validate(1, ExceptionReason.UserNotFound)
    }

    /**
     * Returns a collection of all user's roles assigned to the organizations the user with the specified [userId] is member of.
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun getRolesAssignedToUser(userId: UUID): List<UserRoleInOrganization> = transactionMain {
        getUser(userId).rolesInOrganizations.toList().onEach {
            it.load(UserRoleInOrganization::organization)
            it.load(UserRoleInOrganization::role)
        }
    }

    /**
     * Gets all users within the organizations associated with the [queryingUserId] (i.e., for security reasons, it does not
     * return users from other organizations).
     */
    fun getUsers(queryingUserId: UUID, emailFilter: String? = null, limit: Int = 10): List<User> =
        transactionMain {
            val URIO = UsersRolesInOrganizations
            val urio1 = URIO.alias("urio1")
            val urio2 = URIO.alias("urio2")
            urio1
                .join(urio2, JoinType.INNER, urio1[URIO.organizationId], urio2[URIO.organizationId])
                .join(Users, JoinType.INNER, urio2[URIO.userId], Users.id)
                .slice(Users.columns)
                .select { urio1[URIO.userId] eq queryingUserId }
                .andWhere { Users.email ilike "%${emailFilter}%" }
                .withDistinct()
                .limit(limit)
                .map { User.wrapRow(it) }
        }

    /**
     * Returns [UserDto] object for the user with the specified [userId].
     * Throws [ValidationException] if the specified [userId] doesn't exist.
     */
    fun getUser(userId: UUID): User = transactionMain {
        User.findById(userId).validateNotNull(ExceptionReason.UserNotFound)
    }

    /**
     * Returns the amount of time a password-resetting token is valid. Can be set by a system property `processm.services.timeToResetPassword` (in minutes).
     * Defaults to 1 hour.
     */
    private fun getTimeToResetPassword(): Duration =
        getPropertyIgnoreCase("processm.services.timeToResetPassword")?.let { Duration.ofMinutes(it.toLong()) }
            ?: Duration.ofHours(1L)

    /**
     * Returns the resource bundle [baseName] for [locale] if it is available.
     * Otherwise, returns the resource bundle [baseName] for [defaultLocale].
     *
     * @throws MissingResourceException If [baseName] bundle is not available for [locale] nor for [defaultLocale]
     */
    private fun safeGetBundle(baseName: String, locale: Locale): ResourceBundle =
        try {
            ResourceBundle.getBundle(baseName, locale)
        } catch (_: MissingResourceException) {
            ResourceBundle.getBundle(baseName, defaultLocale)
        }

    /**
     * If there is a registered user with the email address [email], generates a new password-resetting token for them and sends it to that email
     */
    fun sendPasswordResetEmail(email: String): Unit = transactionMain {
        val user = Users.select { Users.email ieq email }.limit(1).map { User.wrapRow(it) }.single()
        val notBefore = Instant.now()
        val notAfter = notBefore.plus(getTimeToResetPassword())
        val requestId = UUID.randomUUID()
        val locale = Locale.forLanguageTag(user.locale)
        val bundle = safeGetBundle("PasswordResetEmail", locale)
        val message = MimeMessage(Session.getInstance(Properties())).apply {
            addRecipients(Message.RecipientType.TO, user.email)
            subject = bundle.getString("subject")
            val url = "${getPropertyIgnoreCase("processm.baseUrl")}/#/reset-password/${requestId}"
            setText(String.format(locale, bundle.getString("body"), url, notAfter))
        }
        PasswordResetRequest.new(requestId) {
            this.user = user
            this.notBefore = notBefore
            this.notAfter = notAfter
            val emailId = UUID.randomUUID()
            this.email = Email.new(emailId) { fromMessage(message) }
            afterCommit {
                producer.produce(EMAIL_TOPIC) {
                    setString(EMAIL_ID, emailId.toString())
                }
            }
        }
    }

    /**
     * If the given [token] is valid, the password of the token's owner is set to [newPassword]
     *
     * @return `true` if the password was changed, `false` otherwise
     */
    fun resetPasswordWithToken(token: UUID, newPassword: String): Boolean = transactionMain {
        loggedScope { logger ->
            val resetRequest = PasswordResetRequest.findById(token) ?: return@transactionMain false
            val now = Instant.now()
            resetRequest.notBefore <= now || return@transactionMain false
            now <= resetRequest.notAfter || return@transactionMain false
            resetRequest.linkClicked == null || return@transactionMain false
            resetRequest.email != null || return@transactionMain false
            resetRequest.email?.sent != null || return@transactionMain false

            resetRequest.user.password = calculatePasswordHash(newPassword)
            resetRequest.linkClicked = now
            logger.debug("A user password has been successfully changed for the user $${resetRequest.user.id} using a token")
            return@transactionMain true
        }
    }

    private fun calculatePasswordHash(password: String) = hasher.encode(password)

    private fun verifyPassword(password: String, passwordHash: String) = hasher.matches(password, passwordHash)

}
