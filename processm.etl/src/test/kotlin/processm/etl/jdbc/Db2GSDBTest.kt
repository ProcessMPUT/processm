package processm.etl.jdbc

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import processm.core.helpers.mapToSet
import processm.core.log.DBLogCleaner
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.EtlProcessMetadata
import processm.dbmodels.models.EtlProcessesMetadata
import processm.etl.Db2Environment
import java.time.Instant
import java.util.*
import kotlin.test.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Db2SalesTest {

    private val batchEventSQL = """
        SELECT * FROM (
        	SELECT *, ROW_NUMBER() OVER (ORDER BY "time:timestamp", "trace_id", "concept:name", "concept:instance") AS "event_id" FROM (
        			SELECT oh.ORDER_NUMBER AS "trace_id", 'place' AS "concept:name", NULL AS "concept:instance", oh.ORDER_DATE AS "time:timestamp" FROM GOSALES.ORDER_HEADER oh  	
        		UNION ALL
        			SELECT oh.ORDER_NUMBER AS "trace_id", 'close' AS "concept:name", NULL AS "concept:instance", oh.ORDER_CLOSE_DATE AS "time:timestamp" FROM GOSALES.ORDER_HEADER oh
        		UNION ALL
        			SELECT od.ORDER_NUMBER AS "trace_id", 'ship' AS "concept:name", od.ORDER_DETAIL_CODE AS "concept:instace", od.SHIP_DATE AS "time:timestamp" FROM GOSALES.ORDER_DETAILS od 
        		UNION ALL
        			SELECT od.ORDER_NUMBER AS "trace_id", 'return' AS "concept:name", od.ORDER_DETAIL_CODE AS "concept:instance", ri.RETURN_DATE AS "time:timestamp" FROM GOSALES.RETURNED_ITEM ri JOIN GOSALES.ORDER_DETAILS od ON ri.ORDER_DETAIL_CODE = od.ORDER_DETAIL_CODE 
        	) sub
        ) sub2        
    """.trimIndent()

    private val incrementalEventSQL = """
        $batchEventSQL
        WHERE "event_id" > CAST(? AS bigint)
    """.trimIndent()


    companion object {
        private val logger = logger()
    }

    private lateinit var externalDB: Db2Environment

    private val dataStoreName = UUID.randomUUID().toString()
    private val etlConfiguratioName = "Db2 GSDB ETL Test"

    private fun createEtlConfiguration(lastEventExternalId: String? = "0") =
        transaction(DBCache.get(dataStoreName).database) {
            val config = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    processType = "Jdbc"
                    name = etlConfiguratioName
                    dataConnector = externalDB.dataConnector
                }
                query = if (lastEventExternalId == null) batchEventSQL else incrementalEventSQL
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

    @BeforeAll
    fun setUp() {
        externalDB = Db2Environment.getGSDB()
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
        DBCache.get(dataStoreName).close()
        DBCache.getMainDBPool().getConnection().use { conn ->
            conn.prepareStatement("""DROP DATABASE "$dataStoreName"""").execute()
        }
    }

    @AfterTest
    fun resetState() {
        DBCache.get(dataStoreName).getConnection().use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
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
        DBCache.getMainDBPool().getConnection().use { conn ->
            conn.prepareStatement("""DROP DATABASE "$dataStoreName"""")
        }
    }

    private val expectedNumberOfTraces = 53267
    private val expectedNumberOfEvents = 562806
    private val newOrderId = 9000000
    private val newOrderDetailsId = 9000000

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
                assertTrue(event.conceptName in setOf("place", "close", "ship", "return"))
                assertTrue(event.identityId!!.mostSignificantBits == 0L)
                assertTrue(event.identityId!!.leastSignificantBits <= Int.MAX_VALUE)
            }

            assertEquals(expectedNumberOfEvents.toString(), etl.lastEventExternalId)
        }
    }


    @Test
    fun `read the first two events one at a time`() {
        val id = createEtlConfiguration()
        val partSize = 3
        var logUUID: UUID? = null
        logger.info("Importing the first event")
        val part1 = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val materialized = etl.toXESInputStream().take(partSize).toList()

            assertEquals("1", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
            return@transaction materialized
        }

        assertEquals(partSize, part1.size)
        for ((i, x) in part1.withIndex())
            logger.debug("$i ${x::class.simpleName} ${x.identityId}")
        assertEquals(1, part1.filterIsInstance<Log>().size)
        assertEquals(logUUID, part1.filterIsInstance<Log>().single().identityId)
        assertEquals(1, part1.filterIsInstance<Trace>().size)
        assertEquals(1, part1.filterIsInstance<Event>().size)
        val event1 = part1.filterIsInstance<Event>().single()
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), event1.identityId)

        logger.info("Importing one more event")
        val part2 = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val part2 = etl.toXESInputStream().take(3).toList()

            for ((i, x) in part2.withIndex())
                logger.debug("$i ${x::class.simpleName} ${x.identityId}")

            assertEquals("2", etl.lastEventExternalId)
            return@transaction part2
        }

        assertEquals(3, part2.size)
        assertEquals(1, part2.filterIsInstance<Log>().size)
        assertEquals(1, part2.filterIsInstance<Trace>().size)
        assertEquals(1, part2.filterIsInstance<Event>().size)
        assertEquals(
            logUUID,
            part2.filterIsInstance<Log>().single().identityId
        )
        val event2 = part2.filterIsInstance<Event>().single()
        assertNotEquals(event1.identityId, event2.identityId)
        assertEquals(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            event2.identityId
        )
    }

    @Test
    fun `read complete XES and then read nothing starting from 0`() {
        val id = createEtlConfiguration()
        val stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.toXESInputStream().toList()
        }

        assertEquals(1, stream.filterIsInstance<Log>().size)
        assertEquals(expectedNumberOfTraces, stream.filterIsInstance<Trace>().mapToSet { it.identityId }.size)
        assertEquals(expectedNumberOfEvents, stream.filterIsInstance<Event>().size)

        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            assertEquals(0, etl.toXESInputStream().count())
        }
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

    private fun `insert and verify new`(id: EntityID<UUID>) {
        try {
            externalDB.connect().use { conn ->
                conn.autoCommit = false
                conn.createStatement().use { stmt ->
                    val id1 = newOrderId
                    val id2 = id1 + 1
                    //1
                    stmt.execute("insert into GOSALES.ORDER_HEADER values ($id1, 'Saidai no honya', 'Saidai no honya', 20339, 3330, 10272, 15, '2021-09-01-00.00.00.000000', '2021-09-16-00.00.00.000000', 6)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 0}, $id1, '2021-09-02-00.00.00.000000', 123110, 0, 31, 25.31, 38.30, 38.30)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 1}, $id1, '2021-09-03-00.00.00.000000', 129160, 0, 31, 25.31, 38.30, 38.30)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 2}, $id1, '2021-09-04-00.00.00.000000', 131120, 0, 31, 25.31, 38.30, 38.30)")
//                    //2
                    stmt.execute("insert into GOSALES.ORDER_HEADER values ($id2, 'Saisyou no honya', 'Saisyou no honya', 20339, 3330, 10272, 15, '2021-09-05-00.00.00.000000', '2021-09-17-00.00.00.000000', 6)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 3}, $id2, '2021-09-06-00.00.00.000000', 145130, 0, 31, 25.31, 38.30, 38.30)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 4}, $id2, '2021-09-07-00.00.00.000000', 144180, 0, 31, 25.31, 38.30, 38.30)")
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 5}, $id2, '2021-09-08-00.00.00.000000', 154150, 0, 31, 25.31, 38.30, 38.30)")
                    //1
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 6}, $id1, '2021-09-11-00.00.00.000000', 132120, 0, 31, 25.31, 38.30, 38.30)")
                    //2
                    stmt.execute("insert into GOSALES.ORDER_DETAILS values (${newOrderDetailsId + 7}, $id2, '2021-09-12-00.00.00.000000', 132120, 0, 31, 25.31, 38.30, 38.30)")
                    //1
                    stmt.execute("insert into GOSALES.RETURNED_ITEM (RETURN_CODE, RETURN_DATE, ORDER_DETAIL_CODE, RETURN_REASON_CODE) values (${newOrderDetailsId + 1},  '2021-09-21-00.00.00.000000', ${newOrderDetailsId + 1}, 1)")
                    //2
                    stmt.execute("insert into GOSALES.RETURNED_ITEM (RETURN_CODE, RETURN_DATE, ORDER_DETAIL_CODE, RETURN_REASON_CODE) values (${newOrderDetailsId + 3},  '2021-09-22-00.00.00.000000',${newOrderDetailsId + 3}, 1)")
                }
                conn.commit()
            }
            val list = transaction(DBCache.get(dataStoreName).database) {
                val etl = ETLConfiguration.findById(id)!!
                return@transaction etl.toXESInputStream().toList()
            }
            for (x in list)
                logger.debug("${x::class} ${x.identityId} ${x.conceptName}")

            assertEquals(1 + 8 + (7 + 7), list.size)
            assertTrue { list[0] is Log }
            assertTrue { list[1] is Trace }
            val trace1Id = list[1].identityId
            assertNotNull(trace1Id)
            assertTrue {
                val e = list[2]
                e is Event && e.conceptName == "place" && e.timeTimestamp == Instant.parse("2021-09-01T00:00:00.00Z")
            }
            assertTrue {
                val e = list[3]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-02T00:00:00.00Z")
            }
            assertTrue {
                val e = list[4]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-03T00:00:00.00Z")
            }
            assertTrue {
                val e = list[5]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-04T00:00:00.00Z")
            }
            assertTrue { list[6] is Trace }
            val trace2Id = list[6].identityId
            assertNotNull(trace2Id)
            assertNotEquals(trace1Id, trace2Id)
            assertTrue {
                val e = list[7]
                e is Event && e.conceptName == "place" && e.timeTimestamp == Instant.parse("2021-09-05T00:00:00.00Z")
            }
            assertTrue {
                val e = list[8]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-06T00:00:00.00Z")
            }
            assertTrue {
                val e = list[9]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-07T00:00:00.00Z")
            }
            assertTrue {
                val e = list[10]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-08T00:00:00.00Z")
            }
            assertTrue { list[11] is Trace }
            assertEquals(trace1Id, list[11].identityId)
            assertTrue {
                val e = list[12]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-11T00:00:00.00Z")
            }
            assertTrue { list[13] is Trace }
            assertEquals(trace2Id, list[13].identityId)
            assertTrue {
                val e = list[14]
                e is Event && e.conceptName == "ship" && e.timeTimestamp == Instant.parse("2021-09-12T00:00:00.00Z")
            }
            assertTrue { list[15] is Trace }
            assertEquals(trace1Id, list[15].identityId)
            assertTrue {
                val e = list[16]
                e is Event && e.conceptName == "close" && e.timeTimestamp == Instant.parse("2021-09-16T00:00:00.00Z")
            }
            assertTrue { list[17] is Trace }
            assertEquals(trace2Id, list[17].identityId)
            assertTrue {
                val e = list[18]
                e is Event && e.conceptName == "close" && e.timeTimestamp == Instant.parse("2021-09-17T00:00:00.00Z")
            }
            assertTrue { list[19] is Trace }
            assertEquals(trace1Id, list[19].identityId)
            assertTrue {
                val e = list[20]
                e is Event && e.conceptName == "return" && e.timeTimestamp == Instant.parse("2021-09-21T00:00:00.00Z")
            }
            assertTrue { list[21] is Trace }
            assertEquals(trace2Id, list[21].identityId)
            assertTrue {
                val e = list[22]
                e is Event && e.conceptName == "return" && e.timeTimestamp == Instant.parse("2021-09-22T00:00:00.00Z")
            }
        } finally {
            externalDB.connect().use { conn ->
                conn.autoCommit = false
                conn.createStatement().use { stmt ->
                    stmt.execute("delete from GOSALES.RETURNED_ITEM where ORDER_DETAIL_CODE >= $newOrderDetailsId")
                    stmt.execute("delete from GOSALES.ORDER_DETAILS where ORDER_NUMBER >= $newOrderId")
                    stmt.execute("delete from GOSALES.ORDER_HEADER where ORDER_NUMBER >= $newOrderId")
                }
                conn.commit()
            }
        }
    }

    @Test
    fun `read all read nothing add new read new`() {
        val id = createEtlConfiguration()
        var list = transaction(DBCache.get(dataStoreName).database) {
            return@transaction ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        assertEquals(1, list.count { it is Log })
        assertEquals(expectedNumberOfTraces, list.filterIsInstance<Trace>().groupBy { it.identityId }.count())
        assertEquals(expectedNumberOfEvents, list.count { it is Event })
        list = transaction(DBCache.get(dataStoreName).database) {
            ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        assertTrue { list.isEmpty() }
        `insert and verify new`(id)
    }

    @Test
    fun `skip read nothing add new read new`() {
        val id = createEtlConfiguration(expectedNumberOfEvents.toString())
        val list = transaction(DBCache.get(dataStoreName).database) {
            return@transaction ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        for (x in list)
            logger.debug("${x::class} ${x.identityId} ${x.conceptName}")
        assertTrue { list.isEmpty() }
        `insert and verify new`(id)
    }
}