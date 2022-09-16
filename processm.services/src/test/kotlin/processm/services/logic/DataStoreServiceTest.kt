package processm.services.logic

import io.mockk.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.select
import processm.core.communication.Producer
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.toFlatSequence
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.models.DATA_CONNECTOR_TOPIC
import processm.dbmodels.models.DataStores
import processm.dbmodels.models.Organizations
import processm.etl.jdbc.toXESInputStream
import processm.services.api.models.JdbcEtlColumnConfiguration
import processm.services.api.models.JdbcEtlProcessConfiguration
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.sql.Types
import java.util.*
import kotlin.test.*

internal class DataStoreServiceTest : ServiceTestBase() {
    private lateinit var producer: Producer
    private lateinit var dataStoreService: DataStoreService

    @BeforeTest
    override fun setUp() {
        super.setUp()
        producer = mockk {
            every { produce(DATA_CONNECTOR_TOPIC, any()) } returns Unit
        }
        dataStoreService = DataStoreService(producer)
    }

    @Test
    fun `Read all data stores assigned to organization`(): Unit = withCleanTables(DataStores, Organizations) {
        val expectedOrgId = createOrganization(name = "Expected").value
        val ignoredOrgId = createOrganization(name = "Ignored").value

        createDataStore(organizationId = expectedOrgId, name = "Expected #1")
        createDataStore(organizationId = expectedOrgId, name = "Expected #2")
        createDataStore(organizationId = ignoredOrgId, name = "Ignored #2")

        val data = dataStoreService.allByOrganizationId(expectedOrgId)

        assertEquals(2, data.size)
        assertNotNull(data.firstOrNull { it.name == "Expected #1" })
        assertNotNull(data.firstOrNull { it.name == "Expected #2" })
        assertNull(data.firstOrNull { it.name == "Ignored #1" })
    }

    @Test
    fun `Create new data store`(): Unit = withCleanTables(DataStores, Organizations) {
        val org = createOrganization().value
        assertTrue(dataStoreService.allByOrganizationId(org).isEmpty())

        val data = dataStoreService.createDataStore(organizationId = org, name = "New data store")

        assertEquals("New data store", data.name)
        assertEquals(org, data.organization.id.value)
        assertNotNull(data.creationDate)

        assertEquals(1, dataStoreService.allByOrganizationId(org).size)
    }

    @Test
    fun `getting specified data store throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        val exception = assertFailsWith<ValidationException>("Specified group does not exist") {
            dataStoreService.getDataStore(UUID.randomUUID())
        }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `getting specified data store returns`(): Unit = withCleanTables(DataStores) {
        val organizationId = createOrganization()
        val dataStoreId = createDataStore(organizationId.value, name = "DataStore1")

        val dataStore = assertNotNull(dataStoreService.getDataStore(dataStoreId.value))

        assertEquals("DataStore1", dataStore.name)
    }

    @Test
    fun `successful renaming of data store returns true`(): Unit = withCleanTables(Organizations, DataStores) {
        val organizationId = createOrganization()
        val dataStoreId = createDataStore(organizationId.value)
        val newName = "DataStore2"

        assertTrue(dataStoreService.renameDataStore(dataStoreId.value, newName))
    }

    @Test
    fun `renaming of data store returns false if not successful`(): Unit = withCleanTables(DataStores) {
        assertFalse(dataStoreService.renameDataStore(UUID.randomUUID(), "DataStore2"))
    }

    @Test
    fun `successful removal of data store returns true`(): Unit = withCleanTables(Organizations, DataStores) {
        val organizationId = createOrganization()
        val dataStoreId = createDataStore(organizationId.value)
        mockkObject(SchemaUtils) {
            every { SchemaUtils.dropDatabase("\"$dataStoreId\"") } just runs

            dataStoreService.removeDataStore(dataStoreId.value)
        }
        assertTrue {
            DataStores.select {
                DataStores.id eq dataStoreId
            }.empty()
        }
    }

    @Test
    fun `getting data connectors throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { getDataConnectors(UUID.randomUUID()) }
    }

    @Test
    fun `creating data connector with connection string throws if nonexistent data store`(): Unit =
        withCleanTables(DataStores) {
            assertDataStoreExistence { createDataConnector(UUID.randomUUID(), "DataConnector1", "connection string") }
        }

    @Test
    fun `creating data connector with connection properties throws if nonexistent data store`(): Unit =
        withCleanTables(DataStores) {
            assertDataStoreExistence { createDataConnector(UUID.randomUUID(), "DataConnector1", emptyMap()) }
        }

    @Test
    fun `removing data connector throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { removeDataConnector(UUID.randomUUID(), UUID.randomUUID()) }
    }

    @Test
    fun `renaming data connector throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { renameDataConnector(UUID.randomUUID(), UUID.randomUUID(), "newName") }
    }

    @Test
    fun `changing ETL process activation state throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { changeEtlProcessActivationState(UUID.randomUUID(), UUID.randomUUID(), true) }
    }

    @Test
    fun `getting case notion suggestions throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { getCaseNotionSuggestions(UUID.randomUUID(), UUID.randomUUID()) }
    }

    @Test
    fun `getting relationship graph throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { getRelationshipGraph(UUID.randomUUID(), UUID.randomUUID()) }
    }

    @Test
    fun `creating automatic ETL process throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence {
            createAutomaticEtlProcess(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "processName",
                emptyList()
            )
        }
    }

    @Test
    fun `getting ETL processes throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { getEtlProcesses(UUID.randomUUID()) }
    }

    @Test
    fun `removing ETL process throws if nonexistent data store`(): Unit = withCleanTables(DataStores) {
        assertDataStoreExistence { removeEtlProcess(UUID.randomUUID(), UUID.randomUUID()) }
    }

    fun assertDataStoreExistence(methodCall: DataStoreService.() -> Unit) {
        val exception =
            assertFailsWith<ValidationException>("The specified data store does not exist or the user has insufficient permissions to it") {
                methodCall(dataStoreService)
            }

        assertEquals(ValidationException.Reason.ResourceNotFound, exception.reason)
    }

    @Test
    fun `create sampling ETL proces`(): Unit = withCleanTables(DataStores, Organizations) {
        val service = DataStoreService(producer)
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
        val service = DataStoreService(producer)
        val logsService = LogsService(producer)
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
