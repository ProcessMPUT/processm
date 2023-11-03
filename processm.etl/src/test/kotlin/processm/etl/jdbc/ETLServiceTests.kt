package processm.etl.jdbc

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.helpers.toUUID
import processm.core.log.DBLogCleaner
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.DataStore
import processm.dbmodels.models.EtlProcessMetadata
import processm.dbmodels.models.EtlProcessesMetadata
import processm.dbmodels.models.Organization
import processm.etl.DBMSEnvironment
import processm.etl.PostgreSQLEnvironment
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

@Tag("ETL")
@Timeout(60, unit = TimeUnit.SECONDS)
class ETLServiceTests {
    companion object {
        // region environment
        private val logger = logger()
        private val dataStoreId = UUID.randomUUID().toString() // do not replace with DBTestHelper.dbName
        private lateinit var externalDB: DBMSEnvironment<*>
        private lateinit var artemis: Artemis
        // endregion

        // region user input
        /**
         * The SQL query for transforming the data into events. One event per row.
         */
        private val getEventSQLContinuous = """
SELECT *, row_number() OVER () AS event_id FROM (
        SELECT 
            'rent' AS "concept:name",
            'start' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            rental_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS "concept:name",
            'complete' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            return_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS "concept:name",
            'complete' AS "lifecycle:transition",
            payment_id AS "concept:instance",
            payment_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL
    ORDER BY "time:timestamp", "concept:instance"
) sub
ORDER BY event_id
LIMIT 1
OFFSET ?::bigint
    """.trimIndent()

        private val getEventSQLOnce = """
SELECT *, row_number() OVER () AS event_id FROM (
        SELECT 
            'rent' AS "concept:name",
            'start' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            rental_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS "concept:name",
            'complete' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            return_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS "concept:name",
            'complete' AS "lifecycle:transition",
            payment_id AS "concept:instance",
            payment_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL
    ORDER BY "time:timestamp", "concept:instance"
) sub
ORDER BY event_id
LIMIT 1
    """.trimIndent()

        /**
         * Retrieves every row with the 10s delay.
         */
        private val getEventSQLContinuousSlow = """
SELECT *, row_number() OVER () AS event_id FROM (
        SELECT 
            'rent' AS "concept:name",
            'start' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            rental_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental CROSS JOIN (SELECT pg_sleep(10)) sleep
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS "concept:name",
            'complete' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            return_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS "concept:name",
            'complete' AS "lifecycle:transition",
            payment_id AS "concept:instance",
            payment_date AS "time:timestamp",
            inventory_id AS trace_id
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL
    ORDER BY "time:timestamp", "concept:instance"
) sub
ORDER BY event_id
LIMIT 1
OFFSET ?::bigint
    """.trimIndent()

        private fun createEtlConfiguration(
            _name: String,
            _refresh: Long?,
            _enabled: Boolean,
            _lastEventExternalId: String?,
            sql: String,
            notify: Boolean = false
        ): EntityID<UUID> {
            val config = transaction(DBCache.get(dataStoreId).database) {
                val config = ETLConfiguration.new {
                    metadata = EtlProcessMetadata.new {
                        processType = "jdbc"
                        name = _name
                        dataConnector = externalDB.dataConnector
                    }
                    query = sql
                    lastEventExternalId = _lastEventExternalId
                    refresh = _refresh
                    enabled = _enabled
                    batch = _refresh === null
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

                config
            }

            if (notify)
                config.notifyUsers()

            return config.id
        }

        private fun setUpArtemis() {
            artemis = Artemis()
            artemis.register()
            artemis.start()
        }

        private fun createDateStore() {
            transactionMain {
                DataStore.new(dataStoreId.toUUID()) {
                    name = "Temporary data store for tests"
                    creationDate = LocalDateTime.now()
                    organization = Organization.all().firstOrNull() ?: Organization.new {
                        name = "Test organization"
                        isPrivate = true
                    }
                }
            }
        }
        // endregion


        // region lifecycle management
        @JvmStatic
        @BeforeAll
        fun setUp() {
            externalDB = PostgreSQLEnvironment.getSakila()
            createDateStore()
            setUpArtemis()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            externalDB.close()
            DBCache.get(dataStoreId).close()
            DBCache.getMainDBPool().getConnection().use { conn ->
                conn.prepareStatement("""DROP DATABASE "$dataStoreId"""").execute()
                conn.prepareStatement("""DELETE FROM data_stores WHERE id='$dataStoreId'""").execute()
            }
            artemis.stop()
        }
        // endregion
    }

    @BeforeTest
    fun initState() {
        createEtlConfiguration("never run", 5L, false, "0", getEventSQLContinuous)
        createEtlConfiguration("never run 2", null, true, "0", getEventSQLContinuous)
        createEtlConfiguration("run once", null, true, null, getEventSQLOnce)
        createEtlConfiguration("repeat", 3L, true, "0", getEventSQLContinuous)
    }

    @AfterTest
    fun resetState() {
        transaction(DBCache.get(dataStoreId).database) {
            val conn = (connection as JdbcConnectionImpl).connection

            conn.createStatement().use { stmt ->
                stmt.executeQuery("""SELECT l.id FROM logs l JOIN etl_configurations e ON l."identity:id"=e.log_identity_id""")
                    .use {
                        while (it.next())
                            DBLogCleaner.removeLog(conn, it.getInt(1))
                    }
            }

            ETLConfigurations.deleteAll()
            EtlProcessesMetadata.deleteAll()
        }
    }

    @Test
    fun `load existing ETL processes and run`() {
        val service = ETLService()
        try {
            service.register()
            assertEquals(ServiceStatus.Stopped, service.status)

            // make sure nothing was imported yet
            transaction(DBCache.get(dataStoreId).database) {
                for (config in ETLConfiguration.all()) {
                    assertEquals(0, q(config.logIdentityId).count())
                }
            }

            service.start()
            assertEquals(ServiceStatus.Started, service.status)

            // wait for imports
            Thread.sleep(5000L)
        } finally {
            service.stop()
        }

        assertEquals(ServiceStatus.Stopped, service.status)

        // verify what was imported
        transaction(DBCache.get(dataStoreId).database) {
            for (config in ETLConfiguration.all()) {
                val stream = q(config.logIdentityId)
                when (config.metadata.name) {
                    "never run", "never run 2" -> assertEquals(0, stream.count())
                    "run once" -> {
                        assertEquals(1, stream.count())
                        val log = stream.first()
                        assertEquals(1, log.traces.count())
                        assertEquals(1, log.traces.first().events.count())
                    }
                    "repeat" -> {
                        assertEquals(1, stream.count())
                        val log = stream.first()
                        assertEquals(1, log.traces.count())
                        // this one should run twice in the time period of 5s
                        assertEquals(2, log.traces.first().events.count())
                    }
                }
            }
        }
    }

    @Test
    fun `run new batch ETL process`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            createEtlConfiguration("new batch ETL", null, true, null, getEventSQLOnce, true)

            // wait for imports
            Thread.sleep(5000L)
        } finally {
            service.stop()
        }


        // verify what was imported
        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("new batch ETL") }.first().id
            }.first()
            val stream = q(config.logIdentityId)

            assertEquals(1, stream.count())
            val log = stream.first()
            assertEquals(1, log.traces.count())
            assertEquals(1, log.traces.first().events.count())
        }
    }

    @Test
    fun `run new continuous ETL process`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            // simulate work
            Thread.sleep(2000L)

            createEtlConfiguration("new continuous ETL", 3L, true, "0", getEventSQLContinuous, true)

            // wait for imports
            Thread.sleep(5000L)
        } finally {
            service.stop()
        }


        // verify what was imported
        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("new continuous ETL") }.first().id
            }.first()
            val stream = q(config.logIdentityId)

            assertEquals(1, stream.count())
            val log = stream.first()
            assertEquals(1, log.traces.count())
            // this one should run twice in the time period of 5s
            assertEquals(2, log.traces.first().events.count())
        }
    }

    @Test
    fun `deactivate continuous ETL process`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            // simulate work
            Thread.sleep(5000L)

            transaction(DBCache.get(dataStoreId).database) {
                val config = ETLConfiguration.find {
                    ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("repeat") }.first().id
                }.first()
                config.enabled = false
                config
            }.notifyUsers()

            // wait for imports
            Thread.sleep(5000L)
        } finally {
            service.stop()
        }


        // verify what was imported
        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("repeat") }.first().id
            }.first()
            val stream = q(config.logIdentityId)

            assertEquals(1, stream.count())
            val log = stream.first()
            assertEquals(1, log.traces.count())
            // this one should run twice in the time period of 5s
            assertEquals(2, log.traces.first().events.count())
        }
    }

    @Test
    fun `deactivate and activate continuous ETL process`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            // simulate work
            Thread.sleep(3500L)

            logger.info("Disabling ETL process repeat")
            transaction(DBCache.get(dataStoreId).database) {
                val config = ETLConfiguration.find {
                    ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name eq "repeat" }
                        .first().id
                }.first()
                config.enabled = false
                config
            }.notifyUsers()

            // simulate break
            logger.info("Break")
            Thread.sleep(3000L)

            logger.info("Enabling ETL process repeat")
            transaction(DBCache.get(dataStoreId).database) {
                val config = ETLConfiguration.find {
                    ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name eq "repeat" }
                        .first().id
                }.first()
                config.enabled = true
                config
            }.notifyUsers()

            // wait for imports
            Thread.sleep(3500L)

        } finally {
            service.stop()
        }


        // verify what was imported
        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name eq "repeat" }
                    .first().id
            }.first()
            val stream = q(config.logIdentityId)

            assertEquals(1, stream.count())
            val log = stream.first()
            assertEquals(2, log.traces.count())
            // this one should run twice in the time period of 7s
            assertEquals(4, log.traces.sumOf { it.events.count() })
        }
    }

    @Test
    fun `prevent concurrent runs of the same process`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            createEtlConfiguration("long-running ETL", 1L, true, "0", getEventSQLContinuousSlow, true)

            // wait for imports
            Thread.sleep(5000L)

            // verify that nothing was imported yet
            transaction(DBCache.get(dataStoreId).database) {
                val config = ETLConfiguration.find {
                    ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("long-running ETL") }
                        .first().id
                }.first()
                val stream = q(config.logIdentityId)

                assertEquals(0, stream.count())
            }
        } finally {
            // stop() waits for running processes
            service.stop()
        }

        // verify that only a single event was imported
        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("long-running ETL") }
                    .first().id
            }.first()
            val stream = q(config.logIdentityId)

            assertEquals(1, stream.count())
            val log = stream.first()
            assertEquals(1, log.traces.count())
            // this one should run twice in the time period of 5s
            assertEquals(1, log.traces.sumOf { it.events.count() })
        }
    }

    @Test
    fun `ETL process with invalid query reports error`() {
        val service = ETLService()
        try {
            service.register()
            service.start()

            createEtlConfiguration("invalid", 5L, true, "0", "?::bigint", true)

            // wait for imports
            Thread.sleep(1000L)

        } finally {
            // stop() waits for running processes
            service.stop()
        }

        transaction(DBCache.get(dataStoreId).database) {
            val config = ETLConfiguration.find {
                ETLConfigurations.metadata eq
                        EtlProcessMetadata.find { EtlProcessesMetadata.name.eq("invalid") }.first().id
            }.first()

            // verify that nothing was imported yet
            val stream = q(config.logIdentityId)
            assertEquals(0, stream.count())

            // and the error is reported
            val errors = config.errors
            assertEquals(1, errors.count())

            val error = errors.first()
            assertNotNull(error.exception)
            println(error.message)
            assertTrue("syntax" in error.message)
        }
    }

    private fun q(identityId: UUID) =
        DBHierarchicalXESInputStream(dataStoreId, Query("where l:id=$identityId"))
}
