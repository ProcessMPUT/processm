package processm.services.logic

import io.mockk.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.dbmodels.models.*
import processm.services.api.models.OrganizationRole
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountServiceTest : ServiceTestBase() {
    private val correctPassword = "pass"
    private val correctPasswordHash = "\$argon2d\$v=19\$m=65536,t=3,p=1\$P0P1NSt1aP8ONWirWMbAWQ\$bDvD/v5/M7T3gRq8BXqbQA"
    private lateinit var accountService: AccountService
    private lateinit var groupServiceMock: GroupService

    @BeforeTest
    override fun setUp() {
        super.setUp()
        groupServiceMock = mockk()
        accountService = AccountService(groupServiceMock)
    }

    @Test
    fun `password verification returns user object if password is correct`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("user@example.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `password verification returns null reference if password is incorrect`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        assertNull(accountService.verifyUsersCredentials("user@example.com", "incorrect_pass"))
    }

    @Test
    fun `password verification throws if nonexistent user`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.verifyUsersCredentials("user2", correctPassword)
        }
        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `password verification is insensitive to username case`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("UsEr@eXaMple.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `successful account registration returns`() =
        withCleanTables(Users, Organizations, UsersRolesInOrganizations, UserGroups) {
            every { groupServiceMock.attachUserToGroup(any(), any()) } just runs

            accountService.createUser("user@example.com", null, "passW0RD")

            val user = User.all().first()
            assertEquals("user@example.com", user.email)
            verify(exactly = 1) { groupServiceMock.attachUserToGroup(user.id.value, any()) }
        }

    @Test
    fun `account registration throws if user already registered`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("The user with the given email already exists.") {
                accountService.createUser("user@example.com", null, "passW0RD")
            }
        assertEquals(Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `account registration is insensitive to user email case`() = withCleanTables(Users, Organizations) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("The user with the given email already exists.") {
                accountService.createUser("uSeR@eXaMpLe.com", null, "passW0RD")
            }
        assertEquals(Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `returns account details of existing user`() = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash)
        val user = assertNotNull(accountService.getAccountDetails(userId.value))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `account details throws if nonexistent user`() = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.getAccountDetails(userId = UUID.randomUUID())
        }
        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password throws if nonexistent user`() = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changePassword(
                userId = UUID.randomUUID(), currentPassword = correctPassword, newPassword = "new_pass"
            )
        }
        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password returns false if current password is incorrect`() = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash)

        assertFalse(accountService.changePassword(userId.value, "incorrect_pass", "new_pass"))
    }

    @Test
    fun `successful password change returns true`() = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash)

        assertTrue(accountService.changePassword(userId.value, correctPassword, "new_pass"))
        assertNotEquals(correctPasswordHash, User.findById(userId)!!.password)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pl_PL", "en", "de-DE", "es-ES_tradnl", "eng", "eng_US"])
    fun `successful locale change runs successfully`(supportedLocale: String) = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash)

        assertDoesNotThrow { accountService.changeLocale(userId.value, supportedLocale) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["goofy", "US", "ab_YZ", "yz_AB", "eng-ENG"])
    fun `changing locale throws if locale is not supported`(unsupportedLocale: String) = withCleanTables(Users) {
        val userId = createUser("user@example.com", correctPasswordHash)

        val exception = assertFailsWith<ValidationException> {
            accountService.changeLocale(userId.value, unsupportedLocale)
        }
        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @ParameterizedTest
    @ValueSource(strings = ["de_DE_DE_de", "de-DE-DE-de"])
    fun `changing locale throws if locale is not properly_formatted`(unsupportedLocale: String) =
        withCleanTables(Users) {
            val userId = createUser("user@example.com", correctPasswordHash, "en_US")

            val exception = assertFailsWith<ValidationException> {
                accountService.changeLocale(userId.value, unsupportedLocale)
            }
            assertEquals(Reason.ResourceFormatInvalid, exception.reason)
        }

    @Test
    fun `changing locale throws if nonexistent user`() = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changeLocale(userId = UUID.randomUUID(), locale = "en_US")
        }
        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns current user roles in assigned organizations`() =
        withCleanTables(Users, Organizations, UsersRolesInOrganizations) {
            val userId = createUser()
            val organizationId1 = createOrganization("Org1")
            val organizationId2 = createOrganization("Org2")
            attachUserToOrganization(userId.value, organizationId1.value, OrganizationRole.reader)
            attachUserToOrganization(userId.value, organizationId2.value, OrganizationRole.writer)

            val userRoles = assertDoesNotThrow { accountService.getRolesAssignedToUser(userId.value) }

            assertEquals(2, userRoles.count())
            assertTrue { userRoles.any { it.organization.name == "Org1" && it.role == OrganizationRole.reader } }
            assertTrue { userRoles.any { it.organization.name == "Org2" && it.role == OrganizationRole.writer } }
        }

    @Test
    fun `user roles in assigned organizations throws if nonexistent user`() =
        withCleanTables(Users, Organizations, UsersRolesInOrganizations) {
            val exception = assertFailsWith<ValidationException> {
                accountService.getRolesAssignedToUser(userId = UUID.randomUUID())
            }
            assertEquals(Reason.ResourceNotFound, exception.reason)
        }
}
