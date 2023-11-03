package processm.services.logic

import processm.dbmodels.models.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrganizationServiceTest : ServiceTestBase() {
    @Test
    fun `getting organization members throws if nonexistent organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getMembers(UUID.randomUUID())
        }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all members attached to organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations, Users, UsersRolesInOrganizations
    ) {
        val organization1 = createOrganization().id.value
        val organization2 = createOrganization().id.value
        val userId1 = createUser("user1@example.com").id.value
        val userId2 = createUser("user2@example.com").id.value
        val userId3 = createUser("user3@example.com").id.value
        attachUserToOrganization(userId1, organization1)
        attachUserToOrganization(userId2, organization2)
        attachUserToOrganization(userId3, organization1)

        val organizationMembers = organizationService.getMembers(organization1)

        assertEquals(2, organizationMembers.count())
        assertTrue { organizationMembers.any { it.user.email == "user1@example.com" } }
        assertTrue { organizationMembers.any { it.user.email == "user3@example.com" } }
    }

    @Test
    fun `getting organization groups throws if nonexistent organization`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception = assertFailsWith<ValidationException>("The specified organization does not exist") {
            organizationService.getOrganizationGroups(UUID.randomUUID())
        }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns all groups related to organization`(): Unit = withCleanTables(AccessControlList, Groups) {
        val organization1 = createOrganization()
        val organization2 = createOrganization()
        val groupId1 = createGroup(organizationId = organization2.id.value)
        val groupId2 = createGroup(organizationId = organization2.id.value)

        val organization1Groups = organizationService.getOrganizationGroups(organization1.id.value)
        assertEquals(1, organization1Groups.count())
        assertTrue { organization1Groups.any { it.id == organization1.sharedGroup.id } }

        val organization2Groups = organizationService.getOrganizationGroups(organization2.id.value)
        assertEquals(3, organization2Groups.count())
        assertTrue { organization2Groups.any { it.id == organization2.sharedGroup.id } }
        assertTrue { organization2Groups.any { it.id == groupId1 } }
        assertTrue { organization2Groups.any { it.id == groupId2 } }
    }

    @Test
    fun `returns organization related to to shared group`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val organizationId = createOrganization()
        createOrganization()

        val organization = organizationService.getOrganizationBySharedGroupId(organizationId.sharedGroup.id.value)
        assertEquals(organizationId.id, organization.id)
    }

    @Test
    fun `getting organization groups throws if nonexistent shared group`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val exception =
            assertFailsWith<ValidationException>("The specified shared group id is not assigned to any organization") {
                organizationService.getOrganizationBySharedGroupId(UUID.randomUUID())
            }

        assertEquals(Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `returns public organizations only if public organizations requested`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val public = createOrganization(name = "public", isPrivate = false)
        val private = createOrganization(name = "private", isPrivate = true)

        val response = organizationService.getAll(true)
        assertEquals(response.size, 1)
        assertEquals(public.id, response[0].id)
        assertEquals("public", response[0].name)
        assertEquals(false, response[0].isPrivate)
    }

    @Test
    fun `returns all organizations only if public and private organizations requested`(): Unit = withCleanTables(
        AccessControlList, Groups, Organizations
    ) {
        val public = createOrganization(name = "public", isPrivate = false)
        val private = createOrganization(name = "private", isPrivate = true)

        val response = organizationService.getAll(false)
        assertEquals(response.size, 2)
        assertEquals(public.id, response[0].id)
        assertEquals("public", response[0].name)
        assertEquals(false, response[0].isPrivate)
        assertEquals(private.id, response[1].id)
        assertEquals("private", response[1].name)
        assertEquals(true, response[1].isPrivate)
    }
}
