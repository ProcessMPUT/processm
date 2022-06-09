package processm.etl.jdbc

import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import processm.core.DBTestHelper
import processm.core.log.AppendingDBXESOutputStream
import processm.core.log.attribute.value
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.EtlProcessMetadata
import processm.dbmodels.models.EtlProcessesMetadata
import processm.etl.DBMSEnvironment
import processm.etl.PostgreSQLEnvironment
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("SqlResolve")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresSakilaWithFloatingPointEventIdsTest {

    val expectedNumberOfEvents = 47954L

    val etlConfigurationName: String = "Test ETL process for PostgreSQL Sakila DB with floating-point event ids"

    fun initExternalDB(): DBMSEnvironment<*> = PostgreSQLEnvironment.getSakila()

    fun lastEventExternalIdFromNumber(numberOfEvents: Long): String {
        // This nonsense here is to achieve precise division by 10 AND ensure the decimal separator is . not ,
        val x = numberOfEvents.toString()
        return x.substring(0, x.length - 1) + "." + x.substring(x.length - 1)
    }

    // region environment
    private val logger = logger()
    private val dataStoreName = DBTestHelper.dbName
    private lateinit var externalDB: DBMSEnvironment<*>
    // endregion


    // region DB-specific SQL

    /**
     * Character to quot column names in an SQL query to allow for special characters and preserve character case
     */
    private val columnQuot = '"'

    // endregion


    private fun createEtlConfiguration() {
        transaction(DBCache.get(dataStoreName).database) {
            val config = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    processType = "Jdbc"
                    name = etlConfigurationName
                    dataConnector = externalDB.dataConnector
                }
                query = getEventSQL
                lastEventExternalId = "0"
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
        }
    }
    // endregion


    private val getEventSQL
        get() = """
            SELECT * FROM (
SELECT ${columnQuot}concept:name${columnQuot}, ${columnQuot}lifecycle:transition${columnQuot}, ${columnQuot}concept:instance${columnQuot}, ${columnQuot}time:timestamp${columnQuot}, ${columnQuot}trace_id${columnQuot}, 0.1*row_number() OVER (ORDER BY ${columnQuot}time:timestamp${columnQuot}, ${columnQuot}concept:instance${columnQuot}) AS ${columnQuot}event_id${columnQuot} FROM (
        SELECT 
            'rent' AS ${columnQuot}concept:name${columnQuot},
            'start' AS ${columnQuot}lifecycle:transition${columnQuot},
            rental_id AS ${columnQuot}concept:instance${columnQuot},
            rental_date AS ${columnQuot}time:timestamp${columnQuot},
            inventory_id AS ${columnQuot}trace_id${columnQuot}
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS ${columnQuot}concept:name${columnQuot},
            'complete' AS ${columnQuot}lifecycle:transition${columnQuot},
            rental_id AS ${columnQuot}concept:instance${columnQuot},
            return_date AS ${columnQuot}time:timestamp${columnQuot},
            inventory_id AS ${columnQuot}trace_id${columnQuot}
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS ${columnQuot}concept:name${columnQuot},
            'complete' AS ${columnQuot}lifecycle:transition${columnQuot},
            payment_id AS ${columnQuot}concept:instance${columnQuot},
            payment_date AS ${columnQuot}time:timestamp${columnQuot},
            inventory_id AS ${columnQuot}trace_id${columnQuot}
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL    
) sub ) core
WHERE ${columnQuot}event_id${columnQuot} > CAST(? AS double precision)
ORDER BY ${columnQuot}event_id${columnQuot}
    """.trimIndent()

    // region lifecycle management
    @BeforeAll
    fun setUp() {
        externalDB = initExternalDB()
        createEtlConfiguration()
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }
    // endregion

    @Test
    fun `read XES from existing data and write it to data store`() {
        var logUUID: UUID? = null
        logger.info("Importing XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name eq etlConfigurationName }
                    .first().id
            }.first()
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
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(
            expectedNumberOfEvents,
            log.traces.first().events.first().attributes["count(event:identity:id)"]?.value
        )

        logger.info("Verifying contents...")
        val invalidContent =
            DBHierarchicalXESInputStream(dataStoreName, Query("where l:id=$logUUID and e:name not in ('rent', 'pay')"))
        assertEquals(0, invalidContent.count())
    }
}