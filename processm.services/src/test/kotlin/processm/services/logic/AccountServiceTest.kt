package processm.services.logic

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
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
import kotlin.test.*

class AccountServiceTest {

    private val correctPassword = "pass"
    private val correctPasswordHash = "\$argon2d\$v=19\$m=65536,t=3,p=1\$P0P1NSt1aP8ONWirWMbAWQ\$bDvD/v5/M7T3gRq8BXqbQA"

    @Before
    @BeforeEach
    fun setUp() {
        mockkObject(DBConnectionPool)
        every { DBConnectionPool.database } returns
                Database.connect("jdbc:h2:mem:regular;", "org.h2.Driver")
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
        Users.insert {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        val user = assertNotNull(accountService.verifyUsersCredentials("user1", correctPassword))
        assertEquals("user1", user.username)

    }

    @Test
    fun `password verification returns null reference if password is incorrect`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        Users.insert {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        assertNull(accountService.verifyUsersCredentials("user1", "incorrect_pass"))
    }

    @Test
    fun `password verification throws if nonexistent user`(): Unit = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        Users.insert {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.verifyUsersCredentials("user2", correctPassword)
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `successful account registration returns`(): Unit = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users, Organizations, OrganizationRoles, UsersRolesInOrganizations)
        OrganizationRole.values().forEach {
                roleName -> OrganizationRoles.insert { it[name] = roleName.toString().toLowerCase() }
        }

        accountService.createAccount("user@example.com", "Org1")

        val user = assertNotNull(User.find(Users.username eq "user@example.com").firstOrNull())
        val organization = assertNotNull(Organization.find(Organizations.name eq "Org1").firstOrNull())
        assertEquals(user.id.value, organization.users.first().id.value)
        assertEquals(organization.id.value, user.organizations.first().id.value)
    }

    @Test
    fun `account registration throws if user already registered`(): Unit = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        SchemaUtils.create(Organizations)
        Users.insert {
            it[username] = "user@example.com"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        val exception = assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
            accountService.createAccount("user@example.com", "Org1")
        }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `account registration throws if organization already registered`(): Unit = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        SchemaUtils.create(Organizations)
        Organizations.insert {
            it[name] = "Org1"
            it[isPrivate] = false
        }

        val exception = assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
            accountService.createAccount("user@example.com", "Org1")
        }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `returns account details of existing user`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        val user = assertNotNull(accountService.getAccountDetails(userId.value))
        assertEquals("user1", user.username)
    }

    @Test
    fun `account details throws if nonexistent user`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.getAccountDetails(userId = 1)
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password throws if nonexistent user`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changePassword(userId = 1, currentPassword = correctPassword, newPassword = "new_pass")
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password returns false if current password is incorrect`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        assertFalse(accountService.changePassword(userId.value, "incorrect_pass", "new_pass"))
    }

    @Test
    fun `successful password change returns true`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        assertTrue(accountService.changePassword(userId.value, correctPassword, "new_pass"))
        assertNotEquals(correctPasswordHash, User.findById(userId)!!.password)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pl_PL", "en", "de-DE", "es-ES_tradnl", "eng", "eng_US"])
    fun `successful locale change runs successfully`(supportedLocale: String) = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        assertDoesNotThrow { accountService.changeLocale(userId.value, supportedLocale) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["goofy", "US", "ab_YZ", "yz_AB", "eng-ENG"])
    fun `changing locale throws if locale is not supported`(unsupportedLocale: String) = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }


        val exception = assertFailsWith<ValidationException> {
            accountService.changeLocale(userId.value, unsupportedLocale)
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @ParameterizedTest
    @ValueSource(strings = ["de_DE_DE_de", "de-DE-DE-de"])
    fun `changing locale throws if locale is not properly_formatted`(unsupportedLocale: String) = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)
        val userId= Users.insertAndGetId {
            it[username] = "user1"
            it[password] = correctPasswordHash
            it[locale] = "en_US"
        }

        val exception = assertFailsWith<ValidationException> {
            accountService.changeLocale(userId.value, unsupportedLocale)
        }
        assertEquals(ValidationException.Reason.ResourceFormatInvalid, exception.reason)
    }

    @Test
    fun `changing locale throws if nonexistent user`() = transaction(DBConnectionPool.database) {

        SchemaUtils.create(Users)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changeLocale(userId = 1, locale = "en_US")
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }
}
