package processm.services.logic

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.communication.email.Email
import processm.core.communication.email.Emails
import processm.dbmodels.models.*
import processm.services.helpers.ExceptionReason
import java.time.Instant
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountServiceTest : ServiceTestBase() {
    private val correctPassword = "pass"
    private val correctPasswordHash = "\$argon2d\$v=19\$m=65536,t=3,p=1\$P0P1NSt1aP8ONWirWMbAWQ\$bDvD/v5/M7T3gRq8BXqbQA"

    @Test
    fun `password verification returns user object if password is correct`(): Unit = withCleanTables(Users) {
        createUser("user@example.com", password = null, passwordHash = correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("user@example.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `password verification returns null reference if password is incorrect`(): Unit = withCleanTables(Users) {
        createUser("user@example.com", password = null, passwordHash = correctPasswordHash)

        assertNull(accountService.verifyUsersCredentials("user@example.com", "incorrect_pass"))
    }

    @Test
    fun `password verification throws if nonexistent user`(): Unit = withCleanTables(Users) {
        createUser("user@example.com", password = null, passwordHash = correctPasswordHash)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.verifyUsersCredentials("user2", correctPassword)
        }
        assertEquals(ExceptionReason.UserNotFound, exception.reason)
    }

    @Test
    fun `password verification is insensitive to username case`(): Unit = withCleanTables(Users) {
        createUser("user@example.com", password = null, passwordHash = correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("UsEr@eXaMple.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `successful account registration returns`(): Unit = withCleanTables(
        AccessControlList, Users, Groups, Organizations, UsersRolesInOrganizations
    ) {
        accountService.create("user@example.com", null, "passW0RD")

        val user = User.all().first()
        assertEquals("user@example.com", user.email)
        assertTrue(user.privateGroup.isImplicit)
    }

    @Test
    fun `account registration throws if user already registered`(): Unit = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("The user with the given email already exists.") {
                accountService.create("user@example.com", null, "passW0RD")
            }
        assertEquals(ExceptionReason.UserAlreadyExists, exception.reason)
    }

    @Test
    fun `account registration is insensitive to user email case`(): Unit = withCleanTables(
        AccessControlList, Users, Groups, Organizations
    ) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("The user with the given email already exists.") {
                accountService.create("uSeR@eXaMpLe.com", null, "passW0RD")
            }
        assertEquals(ExceptionReason.UserAlreadyExists, exception.reason)
    }

    @Test
    fun `returns account details of existing user`(): Unit = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash).id.value
        val user = assertNotNull(accountService.getUser(userId))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `account details throws if nonexistent user`(): Unit = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.getUser(userId = UUID.randomUUID())
        }
        assertEquals(ExceptionReason.UserNotFound, exception.reason)
    }

    @Test
    fun `changing password throws if nonexistent user`(): Unit = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changePassword(
                userId = UUID.randomUUID(), currentPassword = correctPassword, newPassword = "new_pass"
            )
        }
        assertEquals(ExceptionReason.UserNotFound, exception.reason)
    }

    @Test
    fun `changing password returns false if current password is incorrect`(): Unit = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash).id.value

        assertFalse(accountService.changePassword(userId, "incorrect_pass", "new_pass"))
    }

    @Test
    fun `successful password change returns true`(): Unit = withCleanTables(Users) {
        val userId = createUser("user@example.com", password = null, passwordHash = correctPasswordHash).id.value

        assertTrue(accountService.changePassword(userId, correctPassword, "new_pass"))
        assertNotEquals(correctPasswordHash, User.findById(userId)!!.password)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pl-PL", "en"])
    fun `successful locale change runs successfully`(supportedLocale: String): Unit = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash).id.value

        assertDoesNotThrow { accountService.changeLocale(userId, supportedLocale) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["goofy", "US", "ab_YZ", "yz_AB", "eng-ENG", "de-DE", "es-ES-tradnl", "eng", "eng-US"])
    fun `changing locale throws if locale is invalid or not supported`(unsupportedLocale: String): Unit =
        withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash).id.value

        val exception = assertFailsWith<ValidationException> {
            accountService.changeLocale(userId, unsupportedLocale)
        }
        assertTrue { exception.reason == ExceptionReason.CannotChangeLocale || exception.reason == ExceptionReason.InvalidLocale }
    }

    @ParameterizedTest
    @ValueSource(strings = ["de_DE_DE_de", "de-DE-DE-de"])
    fun `changing locale throws if locale is not properly_formatted`(unsupportedLocale: String): Unit =
        withCleanTables(Users) {
            val userId =
                createUser("user@example.com", password = null, passwordHash = correctPasswordHash, "en_US").id.value

            val exception = assertFailsWith<ValidationException> {
                accountService.changeLocale(userId, unsupportedLocale)
            }
            assertEquals(ExceptionReason.InvalidLocale, exception.reason)
        }

    @Test
    fun `changing locale throws if nonexistent user`(): Unit = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changeLocale(userId = UUID.randomUUID(), locale = "en_US")
        }
        assertEquals(ExceptionReason.UserNotFound, exception.reason)
    }

    @Test
    fun `returns current user roles in assigned organizations`(): Unit = withCleanTables(
        AccessControlList, Users, Groups, Organizations, UsersRolesInOrganizations
    ) {
        val userId = createUser().id.value
        val organization1 = createOrganization("Org1")
        val organization2 = createOrganization("Org2")
        attachUserToOrganization(userId, organization1.id.value, RoleType.Reader)
        attachUserToOrganization(userId, organization2.id.value, RoleType.Writer)

        val userRoles = assertDoesNotThrow { accountService.getRolesAssignedToUser(userId) }

        assertEquals(2, userRoles.count())
        assertTrue { userRoles.any { it.organization.name == "Org1" && it.role.name == RoleType.Reader } }
        assertTrue { userRoles.any { it.organization.name == "Org2" && it.role.name == RoleType.Writer } }
    }

    @Test
    fun `user roles in assigned organizations throws if nonexistent user`(): Unit = withCleanTables(
        AccessControlList, Users, Groups, Organizations, UsersRolesInOrganizations
    ) {
        val exception = assertFailsWith<ValidationException> {
            accountService.getRolesAssignedToUser(userId = UUID.randomUUID())
        }
        assertEquals(ExceptionReason.UserNotFound, exception.reason)
    }

    @Test
    fun `request password reset for non-existing user`(): Unit = withCleanTables(Users, PasswordResetRequests) {
        assertFailsWith<NoSuchElementException> { accountService.sendPasswordResetEmail("nonexisting@example.com") }
    }

    @Test
    fun `request password reset for an existing user`(): Unit = withCleanTables(Users, PasswordResetRequests, Emails) {
        val user = createUser()
        val email = user.email
        accountService.sendPasswordResetEmail(email)
        val token =
            PasswordResetRequest.wrapRow(PasswordResetRequests.select { PasswordResetRequests.userId eq user.id }
                .single()).id.value
        val emailBody = Emails.selectAll().map { Email.wrapRow(it) }.single().body.decodeToString()
        assertTrue { token.toString() in emailBody }
    }

    @Test
    fun `reset password with valid token`(): Unit = withCleanTables(Users, PasswordResetRequests, Emails) {
        val user = createUser()
        val email = user.email
        accountService.sendPasswordResetEmail(email)

        val request = PasswordResetRequest.find { PasswordResetRequests.userId eq user.id }.single()
        request.email!!.sent = Instant.now()
        val token = request.id.value

        assertTrue(accountService.resetPasswordWithToken(token, "new_password"))
        assertNotNull(accountService.verifyUsersCredentials(user.email, "new_password"))
    }

    @Test
    fun `fail to reset password with non-existing token`(): Unit =
        withCleanTables(Users, PasswordResetRequests, Emails) {
        val user = createUser()
        val email = user.email
        accountService.sendPasswordResetEmail(email)
        val token = UUID.randomUUID()
        assertFalse(accountService.resetPasswordWithToken(token, "new_password"))
        assertNull(accountService.verifyUsersCredentials(user.email, "new_password"))
    }

    @Test
    fun `fail to reset password with already used token`(): Unit =
        withCleanTables(Users, PasswordResetRequests, Emails) {
            val user = createUser()
            val email = user.email
            accountService.sendPasswordResetEmail(email)

            val request = PasswordResetRequest.find { PasswordResetRequests.userId eq user.id }.single()
            request.email!!.sent = Instant.now()
            val token = request.id.value

            assertTrue(accountService.resetPasswordWithToken(token, "new_password"))
            assertFalse(accountService.resetPasswordWithToken(token, "another_password"))
            assertNull(accountService.verifyUsersCredentials(user.email, "another_password"))
            assertNotNull(accountService.verifyUsersCredentials(user.email, "new_password"))
        }
}
