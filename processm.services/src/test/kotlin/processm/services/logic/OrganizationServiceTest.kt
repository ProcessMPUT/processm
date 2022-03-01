package processm.services.logic

import org.koin.core.component.inject
import org.koin.dsl.module
import processm.dbmodels.models.Organizations
import processm.dbmodels.models.UserGroups
import processm.dbmodels.models.Users
import processm.dbmodels.models.UsersRolesInOrganizations
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrganizationServiceTest : ServiceTestBase() {
    override val dependencyModule = module { single { OrganizationService() } }

    private val organizationService by inject<OrganizationService>()

    @Test
    fun `getting organization members throws if nonexistent organization`(): Unit = withCleanTables(Organizations) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getOrganizationMembers(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all members attached to organization`(): Unit =
        withCleanTables(Organizations, Users, UsersRolesInOrganizations) {
            val organizationId1 = createOrganization()
            val organizationId2 = createOrganization()
            val userId1 = createUser("user1@example.com")
            val userId2 = createUser("user2@example.com")
            val userId3 = createUser("user3@example.com")
            attachUserToOrganization(userId1.value, organizationId1.value)
            attachUserToOrganization(userId2.value, organizationId2.value)
            attachUserToOrganization(userId3.value, organizationId1.value)

            val organizationMembers = organizationService.getOrganizationMembers(organizationId1.value)

            assertEquals(2, organizationMembers.count())
            assertTrue { organizationMembers.any { it.user.email == "user1@example.com" } }
            assertTrue { organizationMembers.any { it.user.email == "user3@example.com" } }
        }

    @Test
    fun `getting organization groups throws if nonexistent organization`(): Unit = withCleanTables(Organizations) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getOrganizationGroups(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all groups related to organization`(): Unit = withCleanTables(UserGroups) {
        val groupId1 = createGroup()
        val groupId2 = createGroup()
        val organizationId1 = createOrganization(sharedGroupId = groupId1.value)
        createOrganization(sharedGroupId = groupId2.value)

        val organizationGroups = organizationService.getOrganizationGroups(organizationId1.value)
        assertEquals(1, organizationGroups.count())
        assertTrue { organizationGroups.any { it.id == groupId1.value } }
    }

    @Test
    fun `returns organization related to to shared group`(): Unit = withCleanTables(Organizations, UserGroups) {
        val groupId1 = createGroup()
        val groupId2 = createGroup()
        val organizationId = createOrganization(sharedGroupId = groupId1.value)
        createOrganization(sharedGroupId = groupId2.value)

        val organization = organizationService.getOrganizationBySharedGroupId(groupId1.value)
        assertEquals(organizationId.value, organization.id)
    }

    @Test
    fun `getting organization groups throws if nonexistent shared group`(): Unit = withCleanTables(Organizations) {
        val exception =
            assertFailsWith<ValidationException>("The specified shared group id is not assigned to any organization") {
                organizationService.getOrganizationBySharedGroupId(UUID.randomUUID())
            }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }
}
