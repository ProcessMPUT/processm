package processm.services.logic

import processm.dbmodels.models.DataStores
import processm.dbmodels.models.Organizations
import kotlin.test.*

internal class DataStoreServiceTest : ServiceTestBase() {
    @Test
    fun `Read all data stores assigned to organization`(): Unit = withCleanTables(DataStores, Organizations) {
        val expectedOrgId = createOrganization(name = "Expected").value
        val ignoredOrgId = createOrganization(name = "Ignored").value

        createDataStore(organizationId = expectedOrgId, name = "Expected #1")
        createDataStore(organizationId = expectedOrgId, name = "Expected #2")
        createDataStore(organizationId = ignoredOrgId, name = "Ignored #2")

        val data = DataStoreService().allByOrganizationId(expectedOrgId)

        assertEquals(2, data.size)
        assertNotNull(data.firstOrNull { it.name == "Expected #1" })
        assertNotNull(data.firstOrNull { it.name == "Expected #2" })
        assertNull(data.firstOrNull { it.name == "Ignored #1" })
    }

    @Test
    fun `Create new data store`(): Unit = withCleanTables(DataStores, Organizations) {
        val service = DataStoreService()
        val org = createOrganization().value
        assertTrue(service.allByOrganizationId(org).isEmpty())

        val data = service.createDataStore(organizationId = org, name = "New data store")

        assertEquals("New data store", data.name)
        assertEquals(org, data.organization.id.value)
        assertNotNull(data.creationDate)

        assertEquals(1, service.allByOrganizationId(org).size)
    }
}