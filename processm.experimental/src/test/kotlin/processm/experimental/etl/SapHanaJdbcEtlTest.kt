package processm.experimental.etl

import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.lifecycle.Startables
import processm.core.DBTestHelper
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.persistence.connection.DBCache
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.jdbc.toXESInputStream
import java.sql.Connection
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Generator(
    val connection: Connection,
    val rng: Random,
    val eketRange: IntRange = 1..5,
    val ekkoRange: IntRange = 1..5,
    val ekpoRange: IntRange = 1..5
) {
    var eban: Int = 0
        private set
    var eket: Int = 0
        private set
    var ekko: Int = 0
        private set
    var ekpo: Int = 0
        private set

    init {
        connection.createStatement().use { stmt ->
            try {
                stmt.execute("drop table EKPO;")
                stmt.execute("drop table EKKO;")
                stmt.execute("drop table EKET;")
                stmt.execute("drop table EBAN;")
            } catch (_: java.sql.SQLException) {
                //silently ignore
            }
            stmt.execute("create table EBAN (id int primary key, text text, timestamp timestamp default CURRENT_TIMESTAMP)")
            stmt.execute("create table EKET (id int primary key, eban int references EBAN(id), text text, timestamp timestamp default CURRENT_TIMESTAMP)")
            stmt.execute("create table EKKO (id int primary key, eban int references EBAN(id), text text, timestamp timestamp default CURRENT_TIMESTAMP)")
            stmt.execute("create table EKPO (id int primary key, ekko int references EKKO(id), text text, timestamp timestamp default CURRENT_TIMESTAMP)")
        }
        connection.autoCommit = false
    }

    fun step() {
        connection.prepareStatement("insert into EBAN (id, text) values (?, ?)").use { stmt ->
            eban++
            stmt.setInt(1, eban)
            stmt.setString(2, "EBAN$eban")
            stmt.executeUpdate()
        }
        connection.prepareStatement("insert into EKET (id, eban, text) values (?, ?, ?)").use { stmt ->
            repeat(rng.nextInt(eketRange)) {
                eket++
                stmt.setInt(1, eket)
                stmt.setInt(2, eban)
                stmt.setString(3, "EBAN$eban EKET$eket")
                stmt.executeUpdate()
            }
        }
        connection.prepareStatement("insert into EKKO  (id, eban, text) values (?, ?, ?)").use { stmtEkko ->
            connection.prepareStatement("insert into EKPO  (id, ekko, text) values (?, ?, ?)").use { stmtEkpo ->
                repeat(rng.nextInt(ekkoRange)) {
                    ekko++
                    stmtEkko.setInt(1, ekko)
                    stmtEkko.setInt(2, eban)
                    stmtEkko.setString(3, "EBAN$eban EKKO$ekko")
                    stmtEkko.executeUpdate()
                    repeat(rng.nextInt(ekpoRange)) {
                        ekpo++
                        stmtEkpo.setInt(1, ekpo)
                        stmtEkpo.setInt(2, ekko)
                        stmtEkpo.setString(3, "EBAN$eban EKKO$ekko EKPO$ekpo")
                        stmtEkpo.executeUpdate()
                    }
                }
            }
        }
        connection.commit()
    }
}

@Tag("slow")
@Tag("ETL")
@Timeout(300, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SapHanaJdbcEtlTest {

    private lateinit var externalDB: SapHanaEnvironment
    private lateinit var generator: Generator

    @BeforeAll
    fun setUp() {
        val container = SapHanaSQLContainer()
        Startables.deepStart(container).join()
        externalDB = SapHanaEnvironment(container)
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }

    @BeforeTest
    fun generator() {
        generator = Generator(externalDB.connect(), Random(42))
    }

    @AfterTest
    fun tearDownGenerator() {
        generator.connection.close()
    }

    private val dataStoreName = DBTestHelper.dbName
    private val etlConfigurationName = "SAP HANA ETL Test"

    private fun getEventSQL(batch: Boolean): String =
        """
        SELECT * FROM (
            SELECT "trace_id", "concept:name", "concept:instance", "time:timestamp", ROW_NUMBER() OVER (ORDER BY "time:timestamp") AS "event_id" FROM (
                SELECT eban as "trace_id", 'eket' AS "concept:name", id as "concept:instance", timestamp as "time:timestamp" FROM eket
                UNION ALL
                SELECT eban as "trace_id", 'ekko' AS "concept:name", id as "concept:instance", timestamp as "time:timestamp" FROM ekko
                UNION ALL
                SELECT ekko.eban as "trace_id", 'ekpo' AS "concept:name", ekpo.id as "concept:instance", ekpo.timestamp as "time:timestamp" FROM ekpo JOIN ekko ON ekpo.ekko=ekko.id
            ) sub2
        ) sub
        """.trimIndent() + (if (!batch) """ WHERE "event_id" > CAST(? AS NUMERIC)""" else "")


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

    @Test
    fun `read XES from existing data`() {
        val id = createEtlConfiguration(null)
        repeat(10) { generator.step() }
        val expectedNumberOfEvents = generator.eket + generator.ekko + generator.ekpo
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            val stream = etl.toXESInputStream().toList()
            assertEquals(1, stream.filterIsInstance<Log>().size)
            assertEquals(generator.eban, stream.filterIsInstance<Trace>().size)
            assertEquals(expectedNumberOfEvents, stream.filterIsInstance<Event>().size)
            assertEquals(expectedNumberOfEvents.toString(), etl.lastEventExternalId)
        }
    }

    @Test
    fun `read XES from existing data in two batches`() {
        val id = createEtlConfiguration()
        repeat(10) { generator.step() }
        val expectedNumberOfEvents = generator.eket + generator.ekko + generator.ekpo
        val stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            etl.toXESInputStream().take(100).toMutableList()
        }
        stream += transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.findById(id)!!
            etl.toXESInputStream().take(100).toList()
        }
        // two logs because we read in two batches
        assertEquals(2, stream.filterIsInstance<Log>().size)
        // eban or eban+1 because we might have split a trace in half
        assertTrue { stream.filterIsInstance<Trace>().size in generator.eban..generator.eban + 1 }
        assertEquals(expectedNumberOfEvents, stream.filterIsInstance<Event>().size)
    }

    @Test
    fun `read, generate, read`() {
        val id = createEtlConfiguration()
        repeat(10) { generator.step() }
        val expectedNumberOfTraces1 = generator.eban
        val expectedNumberOfEvents1 = generator.eket + generator.ekko + generator.ekpo
        val stream1 = transaction(DBCache.get(dataStoreName).database) {
            ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        assertEquals(1, stream1.filterIsInstance<Log>().size)
        assertEquals(expectedNumberOfTraces1, stream1.filterIsInstance<Trace>().size)
        assertEquals(expectedNumberOfEvents1, stream1.filterIsInstance<Event>().size)
        repeat(20) { generator.step() }
        val expectedNumberOfTraces2 = generator.eban - expectedNumberOfTraces1
        val expectedNumberOfEvents2 = generator.eket + generator.ekko + generator.ekpo - expectedNumberOfEvents1
        val stream2 = transaction(DBCache.get(dataStoreName).database) {
            ETLConfiguration.findById(id)!!.toXESInputStream().toList()
        }
        assertEquals(1, stream2.filterIsInstance<Log>().size)
        assertEquals(expectedNumberOfTraces2, stream2.filterIsInstance<Trace>().size)
        assertEquals(expectedNumberOfEvents2, stream2.filterIsInstance<Event>().size)
    }
}