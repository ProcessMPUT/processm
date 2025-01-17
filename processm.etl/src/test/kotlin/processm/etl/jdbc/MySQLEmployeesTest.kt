package processm.etl.jdbc

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import processm.core.DBTestHelper
import processm.core.log.*
import processm.core.persistence.connection.DBCache
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.MySQLEnvironment
import processm.helpers.mapToSet
import processm.logging.logger
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.test.Test

@Tag("ETL")
@Timeout(120, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySQLEmployeesTest {

    companion object {
        private val logger = logger()
    }

    private lateinit var externalDB: MySQLEnvironment

    private fun getEventSQL(batch: Boolean) = """
        select * from (
            select *, row_number() over (order by `time:timestamp`) as `event_id` from (
                    (select 'hire' as `concept:name`, emp_no as `trace_id`, hire_date as `time:timestamp` from employees) 
                union all 
                    (select 'change_salary' as `concept:name`, emp_no as `trace_id`, from_date as `time:timestamp` from salaries) 
                union all 
                    (select 'change_title' as `concept:name`, emp_no as `trace_id`, from_date as `time:timestamp` from titles) 
                union all 
                    (select 'change_department' as `concept:name`, emp_no as `trace_id`, from_date as `time:timestamp` from dept_emp)
            ) sub
        ) sub2 """.trimIndent() +
            (if (!batch) " where  `event_id` > cast(? as unsigned)" else "") +
            " order by `event_id`"


    private val dataStoreName = DBTestHelper.dbName
    private val etlConfigurationName = "MySQL Employees ETL Test"
    private val conceptNames = setOf("hire", "change_salary", "change_title", "change_department")

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

    // region lifecycle management
    @BeforeAll
    fun setUp() {
        externalDB = MySQLEnvironment.getEmployees()
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

    // endregion

    private val expectedNumberOfTraces = 300024
    private val expectedNumberOfEvents = 300024 + 2844047 + 443308 + 331603
    private val newEmployeeId = 500000

    @Test
    fun `read XES from existing data`() {
        val id = createEtlConfiguration()
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val stream = etl.toXESInputStream()
            // this list consumes roughly 2GB if compressed oops are on and 3GB otherwise, so we avoid dynamic expansion to reduce memory footprint
            val list = ArrayList<XESComponent>(7837736)
            list.addAll(stream)
            assertEquals(7837736, list.size)


            assertEquals(1, list.count { it is Log })
            assertEquals(expectedNumberOfTraces, list.filterIsInstance<Trace>().mapToSet { it.identityId }.size)
            assertEquals(expectedNumberOfEvents, list.count { it is Event })

            for (event in list.filterIsInstance<Event>()) {
                assertTrue(event.conceptName in conceptNames)
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
        val (logs, traces, events) = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.getDistribution()
        }

        assertEquals(1, logs)
        assertEquals(expectedNumberOfTraces, traces)
        assertEquals(expectedNumberOfEvents, events)

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
        assertFalse(stream.isEmpty())

        val (logs, traces, events) = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.getDistribution()
        }
        assertEquals(1, logs)
        assertEquals(expectedNumberOfTraces, traces)
        assertEquals(expectedNumberOfEvents, events)

        stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.toXESInputStream().toList()
        }
        assertTrue(stream.isEmpty())
    }

    private fun `insert and verify new`(id: EntityID<UUID>) {
        try {
            externalDB.connect().use { conn ->
                conn.autoCommit = false
                conn.createStatement().use { stmt ->
                    val id1 = newEmployeeId
                    val id2 = newEmployeeId + 1
                    //1
                    stmt.execute("insert into employees values ($id1, '1988-01-01', 'Oishii', 'Okonomiyaki', 'M', '2021-09-01')")
                    stmt.execute("insert into salaries values ($id1, 12345, '2021-09-02', '2021-09-10')")
                    stmt.execute("insert into titles values($id1, 'Lunch', '2021-09-03', '2021-09-04')")
                    stmt.execute("insert into titles values($id1, 'Past Lunch', '2021-09-04', '2021-09-20')")
                    //2
                    stmt.execute("insert into employees values ($id2, '1986-01-01', 'Karee', 'Daisuki', 'M', '2021-09-05')")
                    stmt.execute("insert into salaries values ($id2, 987, '2021-09-06', '2021-09-11')")
                    stmt.execute("insert into titles values($id2, 'Dinner', '2021-09-07', '2021-09-08')")
                    stmt.execute("insert into titles values($id2, 'Past Dinner', '2021-09-08', '2021-09-22')")
                    //1
                    stmt.execute("insert into salaries values ($id1, 123450, '2021-09-11', '2021-09-15')")
                    //2
                    stmt.execute("insert into salaries values ($id2, 9876, '2021-09-12', '2021-09-16')")
                    //1
                    stmt.execute("insert into salaries values ($id1, 1234500, '2021-09-16', '2021-09-20')")
                    //2
                    stmt.execute("insert into salaries values ($id2, 98765, '2021-09-17', '2021-09-21')")
                    //1
                    stmt.execute("insert into titles values($id1, 'Forgotten Lunch', '2021-09-21', '2021-09-30')")
                    //2
                    stmt.execute("insert into titles values($id2, 'Forgotten Dinner', '2021-09-22', '2021-09-30')")
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
                e is Event && e.conceptName == "hire" && e.timeTimestamp == Instant.parse("2021-09-01T00:00:00.00Z")
            }
            assertTrue {
                val e = list[3]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-02T00:00:00.00Z")
            }
            assertTrue {
                val e = list[4]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-03T00:00:00.00Z")
            }
            assertTrue {
                val e = list[5]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-04T00:00:00.00Z")
            }
            assertTrue { list[6] is Trace }
            val trace2Id = list[6].identityId
            assertNotNull(trace2Id)
            assertNotEquals(trace1Id, trace2Id)
            assertTrue {
                val e = list[7]
                e is Event && e.conceptName == "hire" && e.timeTimestamp == Instant.parse("2021-09-05T00:00:00.00Z")
            }
            assertTrue {
                val e = list[8]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-06T00:00:00.00Z")
            }
            assertTrue {
                val e = list[9]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-07T00:00:00.00Z")
            }
            assertTrue {
                val e = list[10]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-08T00:00:00.00Z")
            }
            assertTrue { list[11] is Trace }
            assertEquals(trace1Id, list[11].identityId)
            assertTrue {
                val e = list[12]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-11T00:00:00.00Z")
            }
            assertTrue { list[13] is Trace }
            assertEquals(trace2Id, list[13].identityId)
            assertTrue {
                val e = list[14]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-12T00:00:00.00Z")
            }
            assertTrue { list[15] is Trace }
            assertEquals(trace1Id, list[15].identityId)
            assertTrue {
                val e = list[16]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-16T00:00:00.00Z")
            }
            assertTrue { list[17] is Trace }
            assertEquals(trace2Id, list[17].identityId)
            assertTrue {
                val e = list[18]
                e is Event && e.conceptName == "change_salary" && e.timeTimestamp == Instant.parse("2021-09-17T00:00:00.00Z")
            }
            assertTrue { list[19] is Trace }
            assertEquals(trace1Id, list[19].identityId)
            assertTrue {
                val e = list[20]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-21T00:00:00.00Z")
            }
            assertTrue { list[21] is Trace }
            assertEquals(trace2Id, list[21].identityId)
            assertTrue {
                val e = list[22]
                e is Event && e.conceptName == "change_title" && e.timeTimestamp == Instant.parse("2021-09-22T00:00:00.00Z")
            }
        } finally {
            externalDB.connect().use { conn ->
                conn.autoCommit = false
                conn.createStatement().use { stmt ->
                    stmt.execute("delete from titles where emp_no >= $newEmployeeId")
                    stmt.execute("delete from salaries where emp_no >= $newEmployeeId")
                    stmt.execute("delete from employees where emp_no >= $newEmployeeId")
                }
                conn.commit()
            }
        }
    }

    @Test
    fun `read all read nothing add new read new`() {
        val id = createEtlConfiguration()
        val (logs, traces, events) = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            return@transaction etl.getDistribution()
        }
        assertEquals(1, logs)
        assertEquals(expectedNumberOfTraces, traces)
        assertEquals(expectedNumberOfEvents, events)
        val list = transaction(DBCache.get(dataStoreName).database) {
            ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        assertTrue(list.isEmpty())
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

    private fun ETLConfiguration.getDistribution(): Triple<Int, Int, Int> {
        var logs = 0
        val traces = HashSet<UUID>()
        var events = 0
        for (component in toXESInputStream()) {
            when (component) {
                is Event -> ++events
                is Trace -> traces.add(component.identityId!!)
                is Log -> ++logs
            }
        }
        return Triple(logs, traces.size, events)
    }
}
