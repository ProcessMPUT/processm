package processm.services.logic

import io.mockk.*
import org.jetbrains.exposed.sql.insert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.services.models.*
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountServiceTest : ServiceTestBase() {

    private val correctPassword = "pass"
    private val correctPasswordHash = "\$argon2d\$v=19\$m=65536,t=3,p=1\$P0P1NSt1aP8ONWirWMbAWQ\$bDvD/v5/M7T3gRq8BXqbQA"

    @Before
    @BeforeEach
    fun setUp() {
        groupServiceMock = mockk()
        accountService = AccountService(groupServiceMock)
    }

    lateinit var accountService: AccountService
    lateinit var groupServiceMock: GroupService

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
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `password verification is insensitive to username case`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val user = assertNotNull(accountService.verifyUsersCredentials("UsEr@eXaMple.com", correctPassword))
        assertEquals("user@example.com", user.email)
    }

    @Test
    fun `successful account registration returns`() =
        withCleanTables(Users, Organizations, UsersRolesInOrganizations) {
            val groupId = UUID.randomUUID()
            every { groupServiceMock.ensureSharedGroupExists(any()) } returns groupId
            every { groupServiceMock.attachUserToGroup(any(), groupId) } just runs

            accountService.createAccount("user@example.com", "Org1")

            val organizationMember = UserRolesInOrganizations.all().first()
            assertEquals("user@example.com", organizationMember.user.email)
            assertEquals("Org1", organizationMember.organization.name)
            assertEquals(OrganizationRoleDto.Owner, organizationMember.role.name)
            verify { groupServiceMock.ensureSharedGroupExists(organizationMember.organization.id.value) }
            verify { groupServiceMock.attachUserToGroup(organizationMember.user.id.value, groupId) }
        }

    @Test
    fun `account registration throws if user already registered`() = withCleanTables(Users) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
                accountService.createAccount("user@example.com", "Org1")
            }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
    }

    @Test
    fun `account registration throws if organization already registered`() = withCleanTables(Users, Organizations) {
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
    fun `account registration is sensitive to user email case`() = withCleanTables(Users, Organizations) {
        createUser("user@example.com", correctPasswordHash)

        val exception =
            assertFailsWith<ValidationException>("User and/or organization with specified name already exists") {
                accountService.createAccount("uSeR@eXaMpLe.com", "Org1")
            }
        assertEquals(ValidationException.Reason.ResourceAlreadyExists, exception.reason)
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
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `changing password throws if nonexistent user`() = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changePassword(
                userId = UUID.randomUUID(), currentPassword = correctPassword, newPassword = "new_pass"
            )
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
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
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @ParameterizedTest
    @ValueSource(strings = ["de_DE_DE_de", "de-DE-DE-de"])
    fun `changing locale throws if locale is not properly_formatted`(unsupportedLocale: String) =
        withCleanTables(Users) {
            val userId = createUser("user@example.com", correctPasswordHash, "en_US")

            val exception = assertFailsWith<ValidationException> {
                accountService.changeLocale(userId.value, unsupportedLocale)
            }
            assertEquals(ValidationException.Reason.ResourceFormatInvalid, exception.reason)
        }

    @Test
    fun `changing locale throws if nonexistent user`() = withCleanTables(Users) {
        val exception = assertFailsWith<ValidationException>("Specified user account does not exist") {
            accountService.changeLocale(userId = UUID.randomUUID(), locale = "en_US")
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns current user roles in assigned organizations`() = withCleanTables(Users, Organizations, UsersRolesInOrganizations) {
        val userId = createUser()
        val organizationId1 = createOrganization("Org1")
        val organizationId2 = createOrganization("Org2")
        attachUserToOrganization(userId.value, organizationId1.value, OrganizationRoleDto.Reader)
        attachUserToOrganization(userId.value, organizationId2.value, OrganizationRoleDto.Writer)

        val userRoles = assertDoesNotThrow { accountService.getRolesAssignedToUser(userId.value) }

        assertEquals(2, userRoles.count())
        assertTrue { userRoles.any {it.organization.name == "Org1" && it.role == OrganizationRoleDto.Reader }}
        assertTrue { userRoles.any {it.organization.name == "Org2" && it.role == OrganizationRoleDto.Writer }}
    }

    @Test
    fun `user roles in assigned organizations throws if nonexistent user`() = withCleanTables(Users, Organizations, UsersRolesInOrganizations) {
        val exception = assertFailsWith<ValidationException> {
            accountService.getRolesAssignedToUser(userId = UUID.randomUUID())
        }
        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }
}
