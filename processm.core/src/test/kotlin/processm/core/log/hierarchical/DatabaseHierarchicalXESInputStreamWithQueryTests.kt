package processm.core.log.hierarchical

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.helpers.parseISO8601
import processm.core.log.DatabaseLogCleaner
import processm.core.log.DatabaseXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.logging.logger
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.Query
import java.util.*
import kotlin.system.measureTimeMillis
import kotlin.test.*

class DatabaseHierarchicalXESInputStreamWithQueryTests {
    companion object {
        private val logger = logger()

        // region test log
        private var logId: Int = -1
        private val uuid: UUID = UUID.randomUUID()
        private val eventNames = setOf(
            "invite reviewers",
            "time-out 1", "time-out 2", "time-out 3",
            "get review 1", "get review 2", "get review 3",
            "collect reviews",
            "decide",
            "invite additional reviewer",
            "get review X",
            "time-out X",
            "reject",
            "accept"
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            logger.info("Loading data")
            measureTimeMillis {
                val stream = this::class.java.getResourceAsStream("/xes-logs/JournalReview.xes")
                DatabaseXESOutputStream().use { output ->
                    output.write(XMLXESInputStream(stream).map {
                        if (it is processm.core.log.Log) /* The base class for log */
                            it.identityId = uuid.toString()
                        it
                    })
                }
            }.also { logger.info("Data loaded in ${it}ms.") }

            DBConnectionPool.getConnection().use {
                val response = it.prepareStatement("SELECT id FROM logs ORDER BY id DESC LIMIT 1").executeQuery()
                response.next()

                logId = response.getInt("id")
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            DatabaseLogCleaner.removeLog(logId)
        }
        // endregion
    }

    @Test
    fun basicSelectTest() {
        val begin = "2005-12-31T00:00:00.000Z".parseISO8601()
        val end = "2008-05-05T00:00:00.000Z".parseISO8601()

        val query = Query("select l:name, t:name, e:name, e:timestamp where l:name='JournalReview' and l:id='$uuid'")
        var _stream: DatabaseHierarchicalXESInputStream? = null

        measureTimeMillis {
            _stream = DatabaseHierarchicalXESInputStream(query)
            _stream!!.toFlatSequence().forEach { _ -> }
        }.let { logger.info("Log read in ${it}ms.") }

        val stream = _stream!!

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)

        assertEquals(100, log.traces.count())
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.lifecycleState)
                assertNull(event.lifecycleTransition)
                assertNull(event.orgGroup)
                assertNull(event.orgResource)
                assertNull(event.orgRole)
                assertNull(event.identityId)
            }
        }
    }
}