package processm.services.logic


import processm.dbmodels.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ACLServiceTest : ServiceTestBase() {

    @Test
    fun `only the private group of the other user is available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
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
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
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
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
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

    @Test
    fun `group of the parent organization is available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
            val parent = createOrganization()
            val child = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = child.id.value)
            val group = createGroup(organizationId = parent.id.value)
            organizationService.attachSubOrganization(parent.id.value, child.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, child.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            val groups = aclService.getAvailableGroups(urn, user1.id.value).toList()
            assertTrue { groups.any { it.id == group } }
        }

    @Test
    fun `group of the child organization is not available`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
            val parent = createOrganization()
            val child = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = parent.id.value)
            val group = createGroup(organizationId = child.id.value)
            organizationService.attachSubOrganization(parent.id.value, child.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, parent.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            val groups = aclService.getAvailableGroups(urn, user1.id.value).toList()
            assertFalse { groups.any { it.id == group } }
        }

    @Test
    fun `usersWithAccess owner is listed`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
            val parent = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = parent.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, parent.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            with(aclService.usersWithAccess(urn)) {
                assertEquals(listOf(user1.id.value), this)
            }
        }

    @Test
    fun `usersWithAccess group with RoleType None is not considered by default`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
            val parent = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = parent.id.value)
            val user2 = createUser(userEmail = "user2@example.com", organizationId = parent.id.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, parent.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            aclService.addEntry(urn, user2.privateGroup.id.value, RoleType.None)
            with(aclService.usersWithAccess(urn)) {
                assertEquals(listOf(user1.id.value), this)
            }
            with(aclService.usersWithAccess(urn, role = RoleType.None)) {
                assertEquals(2, size)
                assertTrue { user1.id.value in this }
                assertTrue { user2.id.value in this }
            }
        }

    @Test
    fun `usersWithAccess explicit group`() =
        withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
            val parent = createOrganization()
            val user1 = createUser(userEmail = "user1@example.com", organizationId = parent.id.value)
            val user2 = createUser(userEmail = "user2@example.com", organizationId = parent.id.value)
            val group = createGroup(organizationId = parent.id.value)
            groupService.attachUserToGroup(user2.id.value, group.value)
            val workspace = workspaceService.create("workspace1", user1.id.value, parent.id.value)
            val urn = aclService.getURN(Workspaces, workspace)
            aclService.addEntry(urn, group.value, RoleType.Writer)
            with(aclService.usersWithAccess(urn)) {
                assertEquals(2, size)
                assertTrue { user1.id.value in this }
                assertTrue { user2.id.value in this }
            }
            with(aclService.usersWithAccess(urn, RoleType.Owner)) {
                assertEquals(listOf(user1.id.value), this)
            }
        }

    @Test
    fun `usersWithAccess filtering`() = withCleanTables(AccessControlList, Users, Groups, Organizations, Workspaces) {
        val parent = createOrganization()
        val user1 = createUser(userEmail = "user1@example.com", organizationId = parent.id.value)
        val user2 = createUser(userEmail = "user2@example.com", organizationId = parent.id.value)
        val workspace = workspaceService.create("workspace1", user1.id.value, parent.id.value)
        val urn = aclService.getURN(Workspaces, workspace)
        aclService.addEntry(urn, user2.privateGroup.id.value, RoleType.Reader)
        with(aclService.usersWithAccess(urn, userIDs = listOf(user1.id.value))) {
            assertEquals(listOf(user1.id.value), this)
        }
        with(aclService.usersWithAccess(urn, userIDs = listOf(user2.id.value))) {
            assertEquals(listOf(user2.id.value), this)
        }
    }
}