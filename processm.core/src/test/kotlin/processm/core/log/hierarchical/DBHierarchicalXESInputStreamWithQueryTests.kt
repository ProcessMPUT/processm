package processm.core.log.hierarchical

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import processm.core.helpers.parseISO8601
import processm.core.log.DBLogCleaner
import processm.core.log.DBXESOutputStream
import processm.core.log.XESElement
import processm.core.log.XMLXESInputStream
import processm.core.log.attribute.StringAttr
import processm.core.logging.logger
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.Query
import java.util.*
import kotlin.system.measureTimeMillis
import kotlin.test.*

@Tag("PQL")
class DBHierarchicalXESInputStreamWithQueryTests {
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
        private val lifecyleTransitions = setOf("start", "complete")
        private val orgResources =
            setOf("__INVALID__", "Mike", "Anne", "Wil", "Pete", "John", "Mary", "Carol", "Sara", "Sam", "Pam")
        private val results = setOf("accept", "reject")
        val begin = "2005-12-31T00:00:00.000Z".parseISO8601()
        val end = "2008-05-05T00:00:00.000Z".parseISO8601()

        @BeforeAll
        @JvmStatic
        fun setUp() {
            logger.info("Loading data")
            measureTimeMillis {
                val stream = this::class.java.getResourceAsStream("/xes-logs/JournalReview.xes")
                DBXESOutputStream().use { output ->
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
            DBLogCleaner.removeLog(logId)
        }
        // endregion
    }

    @Test
    fun basicSelectTest() {
        val query = Query("select l:name, t:name, e:name, e:timestamp where l:name='JournalReview' and l:id='$uuid'")
        var _stream: DBHierarchicalXESInputStream? = null

        measureTimeMillis {
            _stream = DBHierarchicalXESInputStream(query)
            _stream!!.toFlatSequence().forEach { _ -> }
        }.let { logger.info("Log read in ${it}ms.") }

        val stream = _stream!!

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        standardAndAllAttributesMatch(log)

        assertEquals(100, log.traces.count())
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(trace)

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
                standardAndAllAttributesMatch(event)
            }
        }
    }

    @Test
    fun scopedSelectAllTest() {
        val query = Query("select t:name, e:*, t:total where l:name='JournalReview' limit l:1")
        val stream = DBHierarchicalXESInputStream(query)

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        standardAndAllAttributesMatch(log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecyleTransitions)
                assertNull(event.orgGroup)
                assertTrue(event.orgResource in orgResources)
                assertNull(event.orgRole)
                assertNull(event.identityId)
                standardAndAllAttributesMatch(event)
            }
        }
    }

    @Test
    fun scopedSelectAll2Test() {
        val query = Query("select t:*, e:*, l:* where l:concept:name like 'Jour%Rev%' and l:id='$uuid'")
        val stream = DBHierarchicalXESInputStream(query)

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertEquals(uuid.toString(), log.identityId)
        assertTrue(with(log.attributes["source"]) { this is StringAttr && this.value == "CPN Tools" })
        assertTrue(with(log.attributes["description"]) { this is StringAttr && this.value == "Log file created in CPN Tools" })
        assertEquals(3, log.eventClassifiers.size)
        assertEquals(2, log.eventGlobals.size)
        assertEquals(1, log.traceGlobals.size)
        standardAndAllAttributesMatch(log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecyleTransitions)
                assertNull(event.orgGroup)
                assertTrue(event.orgResource in orgResources)
                assertNull(event.orgRole)
                assertNull(event.identityId)
                standardAndAllAttributesMatch(event)
            }
        }
    }

    @Test
    fun selectUsingClassifierTest() {
        val query = Query("select e:classifier:concept:name+lifecycle:transition where l:name='_ournal_eview' limit 1")
        val stream = DBHierarchicalXESInputStream(query)

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.identityId)
        assertNull(log.lifecycleModel)
        standardAndAllAttributesMatch(log)

        for (trace in log.traces) {
            assertNull(trace.conceptName)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertNull(event.timeTimestamp)
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecyleTransitions)
                assertNull(event.orgGroup)
                assertNull(event.orgResource)
                assertNull(event.orgRole)
                assertNull(event.identityId)
                standardAndAllAttributesMatch(event)
            }
        }
    }

    @Test
    fun selectAggregationTest() {
        TODO("Add cost:total to traces")
        val query = Query("select min(t:total), avg(t:total), max(t:total) where l:id='$uuid'")
        val stream = DBHierarchicalXESInputStream(query)

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0.1, trace.attributes["min(t:cost:total)"])
        assertEquals(2.72, trace.attributes["avg(t:cost:total)"])
        assertEquals(3.14, trace.attributes["max(t:cost:total)"])

        assertEquals(0, trace.events.count())
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val query = Query("select [e:result], [e:time:timestamp], [org:concept:name] where l:id='$uuid'")
        val stream = DBHierarchicalXESInputStream(query)

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.identityId)
        assertNull(log.lifecycleModel)
        standardAndAllAttributesMatch(log)

        assertEquals(100, log.traces.count())
        for (trace in log.traces) {
            assertNull(trace.conceptName)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end))
                assertNull(event.lifecycleTransition)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.orgGroup)
                assertNull(event.orgResource)
                assertNull(event.orgRole)
                assertTrue(with(event.attributes["result"]) { this is StringAttr && this.value in results })
                standardAndAllAttributesMatch(event)
            }
        }
    }

    @Test
    fun selectExpressionTest() {
        val query = Query(
            "select [e:concept:name] + e:resource, max(timestamp) - \t \n min(timestamp)" +
                    "group event by [e:concept:name], e:resource"
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectAllImplicitTest() {
        val query = Query("")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectConstantsTest() {
        val query = Query("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9, 10 * null, t:null/11, l:D2020-03-12")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectISO8601Test() {
        val query = Query(
            """select 
                    D2020-03-13, 
                    D2020-03-13T16:45, 
                    D2020-03-13T16:45:50, 
                    D2020-03-13T16:45:50.333, 
                    D2020-03-13T16:45+0200, 
                    D2020-03-13T16:45+02:00,
                    D2020-03-13T16:45Z,
                    D20200313, 
                    D20200313T1645, 
                    D20200313T164550, 
                    D20200313T164550.333, 
                    D20200313T1645+0200,
                    D202003131645, 
                    D20200313164550, 
                    D20200313164550.333, 
                    D202003131645+0200,
                    D202003131645Z
                    """
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectIEEE754Test() {
        val query = Query(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE}"
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectBooleanTest() {
        val query = Query("select true, false")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectStringTest() {
        val query = Query("select 'single-quoted', \"double-quoted\"")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun selectNowTest() {
        val query = Query("select now()")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun errorHandlingTest() {
        TODO()
    }

    @Test
    fun whereSimpleTest() {
        val query = Query("where dayofweek(e:timestamp) in (1, 7)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereSimpleWithHoistingTest() {
        val query = Query("where dayofweek(^e:timestamp) in (1, 7)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereSimpleWithHoistingTest2() {
        val query = Query("where dayofweek(^^e:timestamp) in (1, 7)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereLogicExprWithHoistingTest() {
        val query = Query("where not(t:currency = ^e:currency)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereLogicExprTest() {
        val query = Query("where t:currency != e:currency")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereLogicExpr2Test() {
        val query = Query("where not(t:currency = ^e:currency) and t:total is null")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereLogicExpr3Test() {
        val query = Query("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2020-01-01) and t:total is null")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun whereLikeAndMatchesTest() {
        val query = Query("where t:name like 'transaction %' and ^e:resource matches '^[A-Z][a-z]+ [A-Z][a-z]+$'")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupScopeByClassifierTest() {
        val query = Query("group trace by e:classifier:activity")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val query = Query(
            """select t:name, e:name, sum(e:total)
            group event by e:name"""
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupLogByEventStandardAttributeTest() {
        val query = Query(
            """select e:name, sum(e:total)
            group log by e:name"""
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupByImplicitScopeTest() {
        val query = Query("group by e:c:main, [t:branch]")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupByMeaninglessScopeTest() {
        val query = Query("group trace by l:name")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupByImplicitFromSelectTest() {
        val query = Query("select avg(e:total), min(e:timestamp), max(e:timestamp)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupByImplitFromOrderByTest() {
        val query = Query("order by avg(e:total), min(e:timestamp), max(e:timestamp)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun groupByImplicitWithHoistingTest() {
        val query = Query("select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun orderBySimpleTest() {
        val query = Query("order by e:timestamp")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun orderByWithModifierAndScopesTest() {
        val query = Query("order by t:total desc, e:timestamp")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun orderByWithModifierAndScopes2Test() {
        val query = Query("order by e:timestamp, t:total desc")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun orderByExpressionTest() {
        val query = Query("group trace by e:name order by min(^e:timestamp)")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun orderByExpression2Test() {
        val query = Query(
            """group trace by e:name
            |order by [l:basePrice] * avg(^e:total) * 3.141592 desc""".trimMargin()
        )
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun limitSingleTest() {
        val query = Query("limit l:1")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun limitAllTest() {
        val query = Query("limit e:3, t:2, l:1")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun offsetSingleTest() {
        val query = Query("offset l:1")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    @Test
    fun offsetAllTest() {
        val query = Query("offset e:3, t:2, l:1")
        val stream = DBHierarchicalXESInputStream(query)
        TODO()
    }

    fun standardAndAllAttributesMatch(element: XESElement) {
        // TODO("Not implemented")
    }
}