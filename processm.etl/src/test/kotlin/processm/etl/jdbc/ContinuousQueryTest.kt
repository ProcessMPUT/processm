package processm.etl.jdbc

import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.log.*
import processm.core.log.attribute.value
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.etl.DBMSEnvironment
import processm.etl.PostgreSQLEnvironment
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("SqlResolve")
class ContinuousQueryTest {

    companion object {
        // region environment
        private val logger = logger()
        private val dataStoreName = UUID.randomUUID().toString()
        private lateinit var externalDB: DBMSEnvironment<*>
        // endregion

        // region user input
        private val etlConfiguratioName = "Test ETL process for PostgreSQL Sakila DB"

        /**
         * The SQL query for transforming the data into events. One event per row.
         */
        private val getEventSQL = """
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
OFFSET ?::bigint
    """.trimIndent()

        private fun createEtlConfiguration() {
            transaction(DBCache.get(dataStoreName).database) {
                val config = ETLConfiguration.new {
                    name = etlConfiguratioName
                    jdbcUri = externalDB.jdbcUrl
                    user = externalDB.user
                    password = externalDB.password
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


        // region lifecycle management
        @JvmStatic
        @BeforeAll
        fun setUp() {
            externalDB = PostgreSQLEnvironment.getSakila()
            createEtlConfiguration()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            externalDB.close()
            DBCache.get(dataStoreName).close()
            DBCache.getMainDBPool().getConnection().use { conn ->
                conn.prepareStatement("""DROP DATABASE "$dataStoreName"""").execute()
            }
        }
        // endregion
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
    }

    @Test
    fun `read XES from existing data`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            val stream = etl.toXESInputStream()
            val list = stream.toList()

            assertEquals(1, list.count { it is Log })
            // traces may be split into parts:
            assertEquals(4580, list.filterIsInstance<Trace>().groupBy { it.identityId }.count())
            assertEquals(47954, list.count { it is Event })

            for (event in list.filterIsInstance<Event>()) {
                assertTrue(event.conceptName == "rent" || event.conceptName == "pay")
                assertTrue(event.identityId!!.mostSignificantBits == 0L)
                assertTrue(event.identityId!!.leastSignificantBits <= Int.MAX_VALUE)
            }

            assertEquals("47954", etl.lastEventExternalId)
        }
    }

    @Test
    fun `read XES from existing data and write it to data store`() {
        var logUUID: UUID? = null
        logger.info("Importing XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
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
        assertEquals(47954L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)

        logger.info("Verifying contents...")
        val invalidContent =
            DBHierarchicalXESInputStream(dataStoreName, Query("where l:id=$logUUID and e:name not in ('rent', 'pay')"))
        assertEquals(0, invalidContent.count())
    }

    @Test
    fun `read partially XES from existing data and write it to data store then read the remaining XES and write it to data store`() {
        val partSize = 10000
        var logUUID: UUID? = null
        logger.info("Importing the first $partSize XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materialized = etl.toXESInputStream().take(partSize).toList()
                out.write(materialized.asSequence())
            }

            assertEquals("6214", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        var counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )

        var log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(1804L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(6214L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)

        // import the remaining components
        logger.info("Importing the remaining XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }

            assertEquals("47954", etl.lastEventExternalId)
        }

        logger.info("Querying...")
        counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(47954L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)
    }

    @Test
    fun `read XES from existing data and write it to data store then add new data next read XES and write it to data store`() {
        var logUUID: UUID? = null
        logger.info("Importing XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }

            assertEquals("47954", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        // simulate new rental
        val rentalId: Int
        externalDB.connect().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                rentalId = stmt.executeQuery(
                    "INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) VALUES('2021-08-20 09:35:59.987',1613,504,NULL,1) RETURNING rental_id"
                ).use {
                    it.next()
                    it.getInt(1)
                }
                stmt.execute("INSERT INTO payment(customer_id,staff_id,rental_id,amount,payment_date) VALUES(504,1,$rentalId,3.49,'2021-08-20 09:38:17.123')")
            }
        }

        logger.info("Appending XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materializedStream = etl.toXESInputStream().toList()
                out.write(materializedStream.asSequence())

                val events = materializedStream.filterIsInstance<Event>()
                assertEquals(2, events.size)
                assertEquals("rent", events[0].conceptName)
                assertEquals("start", events[0].lifecycleTransition)
                assertEquals("pay", events[1].conceptName)
                assertEquals("complete", events[1].lifecycleTransition)
            }

            assertEquals("47956", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        var counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        var log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(47956L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)

        // simulate new return
        externalDB.connect().use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE rental SET return_date='2021-08-25 17:28:59.387' WHERE rental_id=$rentalId")
                stmt.execute("INSERT INTO payment(customer_id,staff_id,rental_id,amount,payment_date) VALUES(504,1,$rentalId,2.00,'2021-08-25 17:30:00.544')")
            }
        }

        logger.info("Appending XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materializedStream = etl.toXESInputStream().toList()
                out.write(materializedStream.asSequence())

                val events = materializedStream.filterIsInstance<Event>()
                assertEquals(2, events.size)
                assertEquals("rent", events[0].conceptName)
                assertEquals("complete", events[0].lifecycleTransition)
                assertEquals("pay", events[1].conceptName)
                assertEquals("complete", events[1].lifecycleTransition)
            }

            assertEquals("47958", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(47958L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)
    }

    @Test
    fun `read id as Int`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query = "SELECT 987654321::int AS event_id, 123::int AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID(0L, 123L), trace.identityId)
            assertEquals(UUID(0L, 987654321L), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as Long`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query = "SELECT 9876543210::bigint AS event_id, 123::bigint AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID(0L, 123L), trace.identityId)
            assertEquals(UUID(0L, 9876543210L), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as Double`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query = "SELECT 9876543210::double precision AS event_id, 123::double precision AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID(0L, 123L), trace.identityId)
            assertEquals(UUID(0L, 9876543210L), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as UUIDv1`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query =
                    "SELECT 'b4139e40-018d-11ec-9a03-0242ac130003'::uuid AS event_id, 'c17cdfce-018d-11ec-9a03-0242ac130003'::uuid AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID.fromString("c17cdfce-018d-11ec-9a03-0242ac130003"), trace.identityId)
            assertEquals(UUID.fromString("b4139e40-018d-11ec-9a03-0242ac130003"), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as UUIDv4`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query =
                    "SELECT 'c8d47033-b1ad-4668-98e3-21993d7d554b'::uuid AS event_id, '9793827d-8c05-4adf-b0df-6df8eab9ab0b'::uuid AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID.fromString("9793827d-8c05-4adf-b0df-6df8eab9ab0b"), trace.identityId)
            assertEquals(UUID.fromString("c8d47033-b1ad-4668-98e3-21993d7d554b"), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as Text UUID`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query =
                    "SELECT 'c8d47033-b1ad-4668-98e3-21993d7d554b'::text AS event_id, '9793827d-8c05-4adf-b0df-6df8eab9ab0b'::text AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(UUID.fromString("9793827d-8c05-4adf-b0df-6df8eab9ab0b"), trace.identityId)
            assertEquals(UUID.fromString("c8d47033-b1ad-4668-98e3-21993d7d554b"), event.identityId)

            rollback()
        }
    }

    @Test
    fun `read id as arbitrary text`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                name = "Long-based ID test"
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query =
                    "SELECT 'Lorem ipsum dolor sit amet, consectetur adipiscing elit,'::text AS event_id, 'sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.'::text AS trace_id"
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }

            val materializedStream = etl.toXESInputStream().toList()
            assertEquals(1, materializedStream.count { it is Log })
            assertEquals(1, materializedStream.count { it is Trace })
            assertEquals(1, materializedStream.count { it is Event })

            val trace = materializedStream.first { it is Trace }
            val event = materializedStream.first { it is Event }

            assertEquals(
                UUID.nameUUIDFromBytes("sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.".toByteArray()),
                trace.identityId
            )
            assertEquals(
                UUID.nameUUIDFromBytes("Lorem ipsum dolor sit amet, consectetur adipiscing elit,".toByteArray()),
                event.identityId
            )

            rollback()
        }
    }
}
