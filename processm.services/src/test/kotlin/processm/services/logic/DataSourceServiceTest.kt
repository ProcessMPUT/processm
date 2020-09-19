package processm.services.logic

import processm.dbmodels.models.DataSources
import processm.dbmodels.models.Organizations
import kotlin.test.*

internal class DataSourceServiceTest : ServiceTestBase() {
    @Test
    fun `Read all data sources assigned to organization`(): Unit = withCleanTables(DataSources, Organizations) {
        val expectedOrgId = createOrganization(name = "Expected").value
        val ignoredOrgId = createOrganization(name = "Ignored").value

        createDataSource(organizationId = expectedOrgId, name = "Expected #1")
        createDataSource(organizationId = expectedOrgId, name = "Expected #2")
        createDataSource(organizationId = ignoredOrgId, name = "Ignored #2")

        val data = DataSourceService().allByOrganizationId(expectedOrgId)

        assertEquals(2, data.size)
        assertNotNull(data.firstOrNull { it.name == "Expected #1" })
        assertNotNull(data.firstOrNull { it.name == "Expected #2" })
        assertNull(data.firstOrNull { it.name == "Ignored #1" })
    }

    @Test
    fun `Create new data source`(): Unit = withCleanTables(DataSources, Organizations) {
        val service = DataSourceService()
        val org = createOrganization().value
        assertTrue(service.allByOrganizationId(org).isEmpty())

        val data = service.createDataSource(organizationId = org, name = "New data source")

        assertEquals("New data source", data.name)
        assertEquals(org, data.organization.id.value)
        assertNotNull(data.creationDate)

        assertEquals(1, service.allByOrganizationId(org).size)
    }
}