package processm.services.logic

import io.mockk.every
import io.mockk.mockkStatic
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.toFlatSequence
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.models.DataStores
import processm.dbmodels.models.Organizations
import processm.etl.jdbc.toXESInputStream
import processm.services.api.models.JdbcEtlColumnConfiguration
import processm.services.api.models.JdbcEtlProcessConfiguration
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.sql.Types
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

    @Test
    fun `create sampling ETL proces`(): Unit = withCleanTables(DataStores, Organizations) {
        val service = DataStoreService()
        val org = createOrganization().value

        val ds = service.createDataStore(organizationId = org, name = "New data store")

        val dc = service.createDataConnector(ds.id.value, "DC name", "foo://bar")

        val cfg = JdbcEtlProcessConfiguration(
            "query", true, false,
            JdbcEtlColumnConfiguration("traceId", "traceId"), JdbcEtlColumnConfiguration("eventId", "eventId"),
            emptyArray(), BigDecimal(60), "0", Types.INTEGER
        )

        service.createSamplingJdbcEtlProcess(ds.id.value, dc, "dummy", cfg, 3)
    }

    @Test
    fun `create, query and delete sampling ETL proces`(): Unit = withCleanTables(DataStores, Organizations) {
        val service = DataStoreService()
        val logsService = LogsService()
        val org = createOrganization().value

        val ds = service.createDataStore(organizationId = org, name = "New data store")

        val dc = service.createDataConnector(ds.id.value, "DC name", "foo://bar")

        val cfg = JdbcEtlProcessConfiguration(
            "query", true, false,
            JdbcEtlColumnConfiguration("traceId", "traceId"), JdbcEtlColumnConfiguration("eventId", "eventId"),
            emptyArray(), BigDecimal(60), "0", Types.INTEGER
        )

        mockkStatic(ETLConfiguration::toXESInputStream)

        every { any<ETLConfiguration>().toXESInputStream() } returns logFromString(
            """
            a b c
        """.trimIndent()
        ).toFlatSequence()

        val etlProcessId = service.createSamplingJdbcEtlProcess(ds.id.value, dc, "dummy", cfg, 3)

        val logIdentityId = service.getEtlProcessInfo(ds.id.value, etlProcessId).logIdentityId

        val data = ByteArrayOutputStream().use {
            logsService.queryDataStoreJSON(ds.id.value, "where log:identity:id=$logIdentityId")(it)
            return@use it.toByteArray()
        }.decodeToString()

        println(data)

        logsService.removeLog(ds.id.value, logIdentityId)

        service.removeEtlProcess(ds.id.value, etlProcessId)
    }

}