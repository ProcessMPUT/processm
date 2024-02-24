package processm.etl.jdbc

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import processm.core.DBTestHelper
import processm.core.log.*
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.DBMSEnvironment
import processm.logging.logger
import java.sql.Connection
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.test.Test

@Suppress("SqlResolve")
@Tag("ETL")
@Timeout(120, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContinuousQueryTest {

    // region environment
    private val logger = logger()
    private val dataStoreName = DBTestHelper.dbName
    private lateinit var externalDB: DBMSEnvironment<*>
    // endregion


    // region DB-specific SQL

    /**
     * An SQL query to insert a new row into the "rental" table and return the primary key of the newly inserted row.
     * If the db is incapable of doing it in a single query executed with [java.sql.Statement.executeQuery], override [insertNewRental] instead
     */
    protected open val insertNewRentalQuery =
        "INSERT INTO rental(rental_date,inventory_id,customer_id,return_date,staff_id) VALUES(?,?,?,?,?) RETURNING rental_id"


    /**
     * An auxiliary to support databases incapable of returning auto-generated ID of the newly inserted row straight from the INSERT
     */
    protected open fun insertNewRental(
        conn: Connection,
        rental_date: Timestamp,
        inventory_id: Int,
        customer_id: Int,
        return_date: Timestamp?,
        staff_id: Int
    ): Int {
        val rentalId: Int
        conn.prepareStatement(insertNewRentalQuery).use { stmt ->
            stmt.setObject(1, rental_date)
            stmt.setObject(2, inventory_id)
            stmt.setObject(3, customer_id)
            stmt.setObject(4, return_date)
            stmt.setObject(5, staff_id)
            rentalId = stmt.executeQuery().use {
                it.next()
                it.getInt(1)
            }
        }
        return rentalId
    }

    protected open val sqlUUID = "uuid"
    protected open val sqlText = "text"
    protected open val sqlInt = "int"
    protected open val sqlLong = "bigint"

    /**
     * Some databases (e.g., Oracle, DB2) does not allow for SELECT without any FROM, even if only constant expressions are projected
     */
    protected open val dummyFrom = ""

    /**
     * Character to quot column names in an SQL query to allow for special characters and preserve character case
     */
    protected open val columnQuot = '"'

    /**
     * Returns expected last event ID given number of events read so far. Necessary for Oracle which returns row_number as double.
     */
    protected open fun lastEventExternalIdFromNumber(numberOfEvents: Long) = numberOfEvents.toString()
    // endregion

    // region user input
    protected abstract val etlConfigurationName: String

    /**
     * The SQL query for transforming the data into events. One event per row.
     */
    protected open fun getEventSQL(batch: Boolean) = """
            SELECT * FROM (
SELECT ${columnQuot}concept:name${columnQuot}, ${columnQuot}lifecycle:transition${columnQuot}, ${columnQuot}concept:instance${columnQuot}, ${columnQuot}time:timestamp${columnQuot}, ${columnQuot}trace_id${columnQuot}, row_number() OVER (ORDER BY ${columnQuot}time:timestamp${columnQuot}, ${columnQuot}concept:instance${columnQuot}) AS ${columnQuot}event_id${columnQuot} FROM (
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
    """.trimIndent() +
            (if (!batch) " WHERE ${columnQuot}event_id${columnQuot} > CAST(? AS $sqlLong)" else "") +
            " ORDER BY ${columnQuot}event_id${columnQuot}"

    protected open val expectedNumberOfEvents = 47949L
    protected open val expectedNumberOfTracesInTheFirstBatch = 1805L
    protected open val expectedLastEventExternalIdAfterTheFirstBatch
        get() = lastEventExternalIdFromNumber(6214)


    private fun createEtlConfiguration(lastEventExternalId: String? = "0") =
        transaction(DBCache.get(dataStoreName).database) {
            val config = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    name = etlConfigurationName
                    dataConnector = externalDB.dataConnector
                    processType = "jdbc"
                }
                query = getEventSQL(lastEventExternalId == null)
                batch = lastEventExternalId == null
                this.lastEventExternalId = lastEventExternalId
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
    // endregion


    protected abstract fun initExternalDB(): DBMSEnvironment<*>

    // region lifecycle management
    @BeforeAll
    fun setUp() {
        externalDB = initExternalDB()
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }
    // endregion


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

    @Test
    fun `read XES from existing data`() {
        val id = createEtlConfiguration()
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val stream = etl.toXESInputStream()
            val list = stream.toList()

            assertEquals(1, list.count { it is Log })
            // traces may be split into parts:
            assertEquals(4580, list.filterIsInstance<Trace>().groupBy { it.identityId }.count())
            assertEquals(expectedNumberOfEvents.toInt(), list.count { it is Event })

            for (event in list.filterIsInstance<Event>()) {
                assertTrue(event.conceptName == "rent" || event.conceptName == "pay")
                assertTrue(event.identityId!!.mostSignificantBits == 0L)
                assertTrue(event.identityId!!.leastSignificantBits <= Int.MAX_VALUE)
            }

            assertEquals(lastEventExternalIdFromNumber(expectedNumberOfEvents), etl.lastEventExternalId)
        }
    }

    @Test
    fun `read something then read everything then read nothing from existing data starting from null`() {
        val id = createEtlConfiguration(null)
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            assertNull(etl.lastEventExternalId)
            val list = etl.toXESInputStream().take(1000).toList()
            assertEquals(1000, list.size)
        }
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            //lastEventExternalId was not updated, because the previous read was incomplete
            assertNull(etl.lastEventExternalId)

            val list = etl.toXESInputStream().toList()
            assertEquals(1, list.count { it is Log })
            assertEquals(4580, list.filterIsInstance<Trace>().groupBy { it.identityId }.count())
            assertEquals(expectedNumberOfEvents.toInt(), list.count { it is Event })
        }
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            //lastEventExternalId was updated, because we read everything
            assertNotNull(etl.lastEventExternalId)
            val list = etl.toXESInputStream().toList()
            assertTrue { list.isEmpty() }
        }
    }

    @Tag("slow")
    @Test
    @Timeout(180, unit = TimeUnit.SECONDS)
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
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"] )
        assertEquals(
            expectedNumberOfEvents,
            log.traces.first().events.first().attributes["count(event:identity:id)"]
        )

        logger.info("Verifying contents...")
        val invalidContent =
            DBHierarchicalXESInputStream(dataStoreName, Query("where l:id=$logUUID and e:name not in ('rent', 'pay')"))
        assertEquals(0, invalidContent.count())
    }

    @Tag("slow")
    @Test
    @Timeout(150, unit = TimeUnit.SECONDS)
    fun `read partially XES from existing data and write it to data store then read the remaining XES and write it to data store`() {
        val id = createEtlConfiguration()
        val partSize = 10000
        var logUUID: UUID? = null
        logger.info("Importing the first $partSize XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream().take(partSize))
            }

            assertEquals(expectedLastEventExternalIdAfterTheFirstBatch, etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        var counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )

        var log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"] )
        assertEquals(
            expectedNumberOfTracesInTheFirstBatch,
            log.traces.first().attributes["count(trace:identity:id)"]
        )
        assertEquals(6214L, log.traces.first().events.first().attributes["count(event:identity:id)"] )

        // import the remaining components
        logger.info("Importing the remaining XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }

            assertEquals(lastEventExternalIdFromNumber(expectedNumberOfEvents), etl.lastEventExternalId)
        }

        logger.info("Querying...")
        counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"] )
        assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"] )
        assertEquals(
            expectedNumberOfEvents,
            log.traces.first().events.first().attributes["count(event:identity:id)"]
        )
    }


    @Tag("slow")
    @Test
    @Timeout(180, unit = TimeUnit.SECONDS)
    fun `read XES from existing data and write it to data store then add new data next read XES and write it to data store`() {
        val id = createEtlConfiguration()
        var logUUID: UUID? = null
        logger.info("Importing XES...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                out.write(etl.toXESInputStream())
            }

            assertEquals(lastEventExternalIdFromNumber(expectedNumberOfEvents), etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        // simulate new rental
        var rentalId: Int = -1
        try {
            externalDB.connect().use { conn ->
                conn.autoCommit = true
                rentalId = insertNewRental(conn, Timestamp.valueOf("2021-08-20 09:35:59.987"), 1613, 504, null, 1)
                conn.prepareStatement("INSERT INTO payment(customer_id,staff_id,rental_id,amount,payment_date) VALUES(504,1,?,3.49,?)")
                    .use { stmt ->
                        stmt.setObject(1, rentalId)
                        stmt.setObject(2, Timestamp.valueOf("2021-08-20 09:38:17.123"))
                        stmt.execute()
                    }
            }

            logger.info("Appending XES...")
            transaction(DBCache.get(dataStoreName).database) {
                val etl = ETLConfiguration.findById(id)!!
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

                assertEquals(lastEventExternalIdFromNumber(expectedNumberOfEvents + 2), etl.lastEventExternalId)
                logUUID = etl.logIdentityId
            }

            logger.info("Querying...")
            var counts = DBHierarchicalXESInputStream(
                dataStoreName,
                Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
            )
            var log = counts.first()
            assertEquals(1L, log.attributes["count(log:identity:id)"] )
            assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"] )
            assertEquals(
                expectedNumberOfEvents + 2,
                log.traces.first().events.first().attributes["count(event:identity:id)"]
            )

            // simulate new return
            externalDB.connect().use { conn ->
                conn.autoCommit = true
                conn.prepareStatement("UPDATE rental SET return_date=? WHERE rental_id=?").use { stmt ->
                    stmt.setObject(1, Timestamp.valueOf("2021-08-25 17:28:59.387"))
                    stmt.setObject(2, rentalId)
                    stmt.execute()
                }
                conn.prepareStatement("INSERT INTO payment(customer_id,staff_id,rental_id,amount,payment_date) VALUES(504,1,?,2.00,?)")
                    .use { stmt ->
                        stmt.setObject(1, rentalId)
                        stmt.setObject(2, Timestamp.valueOf("2021-08-25 17:30:00.544"))
                        stmt.execute()
                    }
            }

            logger.info("Appending XES...")
            transaction(DBCache.get(dataStoreName).database) {
                val etl = ETLConfiguration.findById(id)!!
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

                assertEquals(lastEventExternalIdFromNumber(expectedNumberOfEvents + 4), etl.lastEventExternalId)
                logUUID = etl.logIdentityId
            }

            logger.info("Querying...")
            counts = DBHierarchicalXESInputStream(
                dataStoreName,
                Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
            )
            log = counts.first()
            assertEquals(1L, log.attributes["count(log:identity:id)"] )
            assertEquals(4580L, log.traces.first().attributes["count(trace:identity:id)"] )
            assertEquals(
                expectedNumberOfEvents + 4,
                log.traces.first().events.first().attributes["count(event:identity:id)"]
            )
        } finally {
            externalDB.connect().use { conn ->
                conn.autoCommit = false
                conn.prepareStatement("delete from payment where rental_id=?").use { stmt ->
                    stmt.setObject(1, rentalId)
                    stmt.execute()
                }
                conn.prepareStatement("delete from rental where rental_id=?").use { stmt ->
                    stmt.setObject(1, rentalId)
                    stmt.execute()
                }
                conn.commit()
            }
        }
    }

    @Test
    fun `read id as Int`() {
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                    processType = "jdbc"
                }
                query =
                    "SELECT CAST(987654321 AS $sqlInt) AS ${columnQuot}event_id${columnQuot}, CAST(123 AS $sqlInt) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                    processType = "jdbc"
                }
                query =
                    "SELECT CAST(9876543210 AS $sqlLong) AS ${columnQuot}event_id${columnQuot}, CAST(123 AS $sqlLong) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                }
                query =
                    "SELECT CAST(9876543210 AS double precision) AS ${columnQuot}event_id${columnQuot}, CAST(123 AS double precision) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                }
                query =
                    "SELECT CAST('b4139e40-018d-11ec-9a03-0242ac130003' AS $sqlUUID) AS ${columnQuot}event_id${columnQuot}, CAST('c17cdfce-018d-11ec-9a03-0242ac130003' AS $sqlUUID) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                }
                query =
                    "SELECT CAST('c8d47033-b1ad-4668-98e3-21993d7d554b' AS $sqlUUID) AS ${columnQuot}event_id${columnQuot}, CAST('9793827d-8c05-4adf-b0df-6df8eab9ab0b' AS $sqlUUID) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                }
                query =
                    "SELECT CAST('c8d47033-b1ad-4668-98e3-21993d7d554b' AS $sqlText) AS ${columnQuot}event_id${columnQuot}, CAST('9793827d-8c05-4adf-b0df-6df8eab9ab0b' AS $sqlText) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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
                metadata = EtlProcessMetadata.new {
                    processType = "jdbc"
                    name = "Long-based ID test"
                    dataConnector = externalDB.dataConnector
                }
                query =
                    "SELECT CAST('Lorem ipsum dolor sit amet, consectetur adipiscing elit,' AS $sqlText) AS ${columnQuot}event_id${columnQuot}, CAST('sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.' AS $sqlText) AS ${columnQuot}trace_id${columnQuot} $dummyFrom"
                lastEventExternalId = null
                batch = true
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
            materializedStream.assertDistribution(1, 1, 1)

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

    private fun Iterable<XESComponent>.assertDistribution(logs: Int, traces: Int, events: Int) {
        assertEquals(logs, count { it is Log })
        assertEquals(traces, count { it is Trace })
        assertEquals(events, count { it is Event })
    }
}
