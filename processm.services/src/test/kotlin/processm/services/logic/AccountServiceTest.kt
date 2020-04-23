package processm.services.logic

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.persistence.DBConnectionPool
import processm.services.models.*
import java.util.*
import kotlin.test.*

class AccountServiceTest {

    private val correctPassword = "pass"
    private val correctPasswordHash = "\$argon2d\$v=19\$m=65536,t=3,p=1\$P0P1NSt1aP8ONWirWMbAWQ\$bDvD/v5/M7T3gRq8BXqbQA"

    @Before
    @BeforeEach
    fun setUp() {
        mockkObject(DBConnectionPool)
        every { DBConnectionPool.database } returns Database.connect("jdbc:h2:mem:regular;", "org.h2.Driver")
        accountService = AccountService()
    }

    @After
    @AfterEach
    fun tearDown() {
        unmockkObject(DBConnectionPool)
    }

    lateinit var accountService: AccountService

    @Test
    fun `password verification returns user object if password is correct`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        createUser("user@example.com", correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("user@example.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `password verification returns null reference if password is incorrect`() =
        transaction(DBConnectionPool.database) {
            SchemaUtils.create(Users)
            createUser("user@example.com", correctPasswordHash)

            assertNull(accountService.verifyUsersCredentials("user@example.com", "incorrect_pass"))
        }

    @Test
    fun `password verification throws if nonexistent user`(): Unit = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        createUser("user@example.com", correctPasswordHash)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.verifyUsersCredentials("user2", correctPassword)
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `password verification is insensitive to username case`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        createUser("user@example.com", correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("UsEr@eXaMple.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `successful account registration returns`(): Unit = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users, Organizations, OrganizationRoles, UsersRolesInOrganizations)
        OrganizationRole.values().forEach { roleName ->
            OrganizationRoles.insert { it[name] = roleName.toString().toLowerCase() }
        }

        accountService.createAccount("user@example.com", "Org1")
        val user = assertNotNull(User.find(Users.email eq "user@example.com").firstOrNull())
        val organization = assertNotNull(Organization.find(Organizations.name eq "Org1").firstOrNull())
        assertEquals(user.id.value, organization.users.first().id.value)
        assertEquals(organization.id.value, user.organizations.first().id.value)
    }

    @Test
    fun `account registration throws if user already registered`(): Unit = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users, Organizations)
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
                accountService.createAccount("user@example.com", "Org1")
            }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `account registration throws if organization already registered`(): Unit =
        transaction(DBConnectionPool.database) {
            SchemaUtils.create(Users, Organizations)
            Organizations.insert {
                it[name] = "Org1"
                it[isPrivate] = false
            }
            val exception =
                assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
                    accountService.createAccount("user@example.com", "Org1")
                }
            assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
        }

    @Test
    fun `account registration is sensitive to user email case`(): Unit = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users, Organizations)
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
                accountService.createAccount("uSeR@eXaMpLe.com", "Org1")
            }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `returns account details of existing user`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val userId = createUser("user@example.com", correctPasswordHash)
        val user = assertNotNull(accountService.getAccountDetails(userId.value))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `account details throws if nonexistent user`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.getAccountDetails(userId = UUID.randomUUID())
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password throws if nonexistent user`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changePassword(
                userId = UUID.randomUUID(), currentPassword = correctPassword, newPassword = "new_pass"
            )
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password returns false if current password is incorrect`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val userId = createUser("user@example.com", correctPasswordHash)

        assertFalse(accountService.changePassword(userId.value, "incorrect_pass", "new_pass"))
    }

    @Test
    fun `successful password change returns true`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val userId = createUser("user@example.com", correctPasswordHash)

        assertTrue(accountService.changePassword(userId.value, correctPassword, "new_pass"))
        assertNotEquals(correctPasswordHash, User.findById(userId)!!.password)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pl_PL", "en", "de-DE", "es-ES_tradnl", "eng", "eng_US"])
    fun `successful locale change runs successfully`(supportedLocale: String) = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val userId = createUser("user@example.com", correctPasswordHash)

        assertDoesNotThrow { accountService.changeLocale(userId.value, supportedLocale) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["goofy", "US", "ab_YZ", "yz_AB", "eng-ENG"])
    fun `changing locale throws if locale is not supported`(unsupportedLocale: String) =
        transaction(DBConnectionPool.database) {
            SchemaUtils.create(Users)
            val userId = createUser("user@example.com", correctPasswordHash)

            val exception = assertFailsWith<ValidationException> {
                accountService.changeLocale(userId.value, unsupportedLocale)
            }
            assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
        }

    @ParameterizedTest
    @ValueSource(strings = ["de_DE_DE_de", "de-DE-DE-de"])
    fun `changing locale throws if locale is not properly_formatted`(unsupportedLocale: String) =
        transaction(DBConnectionPool.database) {
            SchemaUtils.create(Users)
            val userId = createUser("user@example.com", correctPasswordHash, "en_US")

            val exception = assertFailsWith<ValidationException> {
                accountService.changeLocale(userId.value, unsupportedLocale)
            }
            assertEquals(ValidationException.Reason.ResourceFormatInvalid, exception.reason)
        }

    @Test
    fun `changing locale throws if nonexistent user`() = transaction(DBConnectionPool.database) {
        SchemaUtils.create(Users)
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changeLocale(userId = UUID.randomUUID(), locale = "en_US")
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    private fun Transaction.createUser(userEmail: String, passwordHash: String, locale: String = "en_US") =
        Users.insertAndGetId {
            it[email] = userEmail
            it[password] = passwordHash
            it[Users.locale] = locale
        }
}
