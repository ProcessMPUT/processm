package processm.etl.jdbc

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import processm.core.DBTestHelper
import processm.core.helpers.mapToSet
import processm.core.log.*
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.OracleEnvironment
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("ETL")
@Timeout(90, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleOTSampleDbTest {

    private lateinit var externalDB: OracleEnvironment

    private fun getEventSQL(batch: Boolean) = """
        SELECT * FROM (
            SELECT "trace_id", "concept:name", "concept:instance", "time:timestamp", ROW_NUMBER() OVER (ORDER BY "time:timestamp") AS "event_id" FROM (
                    SELECT e.EMPLOYEE_ID AS "trace_id", 'hire' AS "concept:name", NULL AS "concept:instance", e.HIRE_DATE AS "time:timestamp" FROM hr.EMPLOYEES e 
                UNION ALL
                    SELECT jh.EMPLOYEE_ID AS "trace_id", 'change_position' AS "concept:name", NULL AS "concept:instance", jh.END_DATE AS "time:timestamp" FROM hr.JOB_HISTORY jh
                UNION ALL
                    SELECT o.SALES_REP_ID AS "trace_id", 'handle_order' AS "concept:name", o.ORDER_ID AS "concept:instance", o.ORDER_DATE AS "time:timestamp" FROM oe.ORDERS o WHERE o.SALES_REP_ID IS NOT NULL 
            ) sub
        ) sub2        
    """.trimIndent() + (if (!batch) """WHERE "event_id" > CAST(? AS NUMBER(*, 0))""" else "")

    @BeforeAll
    fun setUp() {
        externalDB = OracleEnvironment.getOTSampleDb()
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }

    @AfterTest
    fun resetState() {
        DBCache.get(dataStoreName).getConnection().use { conn ->
            conn.autoCommit = false

            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE etl_configurations SET last_event_external_id=0")
                stmt.executeQuery("""SELECT l.id FROM logs l JOIN etl_configurations e ON l."identity:id"=e.log_identity_id""")
                    .use {
                        while (it.next())
                            DBLogCleaner.removeLog(conn, it.getInt(1))
                    }
            }

            conn.commit()
        }
        transaction(DBCache.get(dataStoreName).database) {
            ETLConfigurations.deleteAll()
        }
    }

    private val logger = logger()
    private val dataStoreName = DBTestHelper.dbName
    private val etlConfigurationName = "Oracle Sample DB ETL Test"

    private fun createEtlConfiguration(lastEventExternalId: String? = "0") =
        transaction(DBCache.get(dataStoreName).database) {
            val config = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = etlConfigurationName
                    dataConnector = externalDB.dataConnector
                }
                query = getEventSQL(lastEventExternalId == null)
                this.lastEventExternalId = lastEventExternalId
                batch = lastEventExternalId == null
            }

            ETLColumnToAttributeMap.new {
                configuration = config
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = config
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }
            return@transaction config.id
        }

    private val expectedNumberOfTraces = 107
    private val expectedNumberOfEvents = 187

    @Test
    fun `read XES from existing data`() {
        val id = createEtlConfiguration()
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val stream = etl.toXESInputStream()
            val list = stream.toList()

            assertEquals(1, list.count { it is Log })
            assertEquals(expectedNumberOfTraces, list.filterIsInstance<Trace>().groupBy { it.identityId }.count())
            assertEquals(expectedNumberOfEvents, list.count { it is Event })

            for (event in list.filterIsInstance<Event>()) {
                assertTrue(event.conceptName in setOf("hire", "change_position", "handle_order"))
                assertTrue(event.identityId!!.mostSignificantBits == 0L)
                assertTrue(event.identityId!!.leastSignificantBits <= Int.MAX_VALUE)
            }

            assertEquals(expectedNumberOfEvents.toDouble().toString(), etl.lastEventExternalId)
        }
    }

    @Test
    fun `read XES from existing data and write it to data store`() {
        val id = createEtlConfiguration()
        var logUUID: UUID? = null
        logger.info("Importing XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        val counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )

        val log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"] )
        assertEquals(expectedNumberOfTraces.toLong(), log.traces.first().attributes["count(trace:identity:id)"] )
        assertEquals(
            expectedNumberOfEvents.toLong(),
            log.traces.first().events.first().attributes["count(event:identity:id)"] 
        )

        logger.info("Verifying contents...")
        val invalidContent =
            DBHierarchicalXESInputStream(
                dataStoreName,
                Query("where l:id=$logUUID and e:name not in ('hire', 'change_position', 'handle_order')")
            )
        assertEquals(0, invalidContent.count())
    }

    @Test
    fun `read partially XES from existing data and write it to data store then read the remaining XES and write it to data store`() {
        val id = createEtlConfiguration()
        val partSize = 100
        var logUUID: UUID? = null
        logger.info("Importing the first $partSize XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materialized = etl.toXESInputStream().take(partSize).toList()
                out.write(materialized.asSequence())
            }

            assertEquals("49.0", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        var counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )

        var log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"] )
        assertEquals(47L, log.traces.first().attributes["count(trace:identity:id)"] )
        assertEquals(49L, log.traces.first().events.first().attributes["count(event:identity:id)"] )

        // import the remaining components
        logger.info("Importing the remaining XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }
            assertEquals(expectedNumberOfEvents.toDouble().toString(), etl.lastEventExternalId)
        }

        logger.info("Querying...")
        counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"] )
        assertEquals(expectedNumberOfTraces.toLong(), log.traces.first().attributes["count(trace:identity:id)"] )
        assertEquals(
            expectedNumberOfEvents.toLong(),
            log.traces.first().events.first().attributes["count(event:identity:id)"] 
        )
    }

    @Test
    fun `read something then read everything then read nothing from null`() {
        val id = createEtlConfiguration(null)

        var stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.toXESInputStream().take(100).toList()
        }
        assertFalse { stream.isEmpty() }

        stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.toXESInputStream().toList()
        }
        assertEquals(1, stream.filterIsInstance<Log>().size)
        assertEquals(expectedNumberOfTraces, stream.filterIsInstance<Trace>().mapToSet { it.identityId }.size)
        assertEquals(expectedNumberOfEvents, stream.filterIsInstance<Event>().size)

        stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.toXESInputStream().toList()
        }
        assertTrue { stream.isEmpty() }
    }
}
