package processm.services.logic


import processm.dbmodels.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ACLServiceTest : ServiceTestBase() {

    @Test
    fun `only the private group of the other user is available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations) {
            val org = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = org.id.value)
            val user2 = createUser(userEmail = "user2@example.com", organizationId = org.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, org.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            //The commit seems to be necessary, but I don't see why
            commit()
            val groups = aclService.getAvailableGroups(urn, user1.id.value).toList()
            assertEquals(1, groups.size)
            assertTrue { groups[0].isImplicit && user2 in groups[0].members }
        }

    @Test
    fun `the other organization is available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations) {
            val org1 = createOrganization()
            val org2 = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = org1.id.value)
            groupService.attachUserToGroup(user1.id.value, org2.sharedGroup.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, org1.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            //The commit seems to be necessary, but I don't see why
            commit()
            val groups = aclService.getAvailableGroups(urn, user1.id.value).toList()
            assertEquals(1, groups.size)
            assertTrue { groups[0].isShared && groups[0].organizationId?.id == org2.id }
        }

    @Test
    fun `no groups are available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations) {
            val org1 = createOrganization()
            val org2 = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = org1.id.value)
            createUser(userEmail = "user2@example.com", organizationId = org2.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, org1.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            //The commit seems to be necessary, but I don't see why
            commit()
            val groups = aclService.getAvailableGroups(urn, user1.id.value).toList()
            assertTrue { groups.isEmpty() }
        }
}