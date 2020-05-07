package processm.core.log.hierarchical

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import processm.core.helpers.parseISO8601
import processm.core.log.*
import processm.core.log.attribute.StringAttr
import processm.core.log.attribute.value
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
        var _stream: DBHierarchicalXESInputStream? = null

        measureTimeMillis {
            _stream = q("select l:name, t:name, e:name, e:timestamp where l:name='JournalReview' and l:id='$uuid'")
            _stream!!.toFlatSequence().forEach { _ -> }
        }.let { logger.info("Log read in ${it}ms.") }

        val stream = _stream!!

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        standardAndAllAttributesMatch(log, log)

        assertEquals(100, log.traces.count())
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

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
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun scopedSelectAllTest() {
        val stream = q("select t:name, e:*, t:total where l:name='JournalReview' limit l:1")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

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
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun scopedSelectAll2Test() {
        val stream = q("select t:*, e:*, l:* where l:concept:name like 'Jour%Rev%' and l:id='$uuid'")

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
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= 0)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

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
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectUsingClassifierTest() {
        val stream =
            q("select [e:classifier:concept:name+lifecycle:transition] where l:name like '_ournal_eview' limit l:1")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.identityId)
        assertNull(log.lifecycleModel)
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            assertNull(trace.conceptName)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

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

                assertTrue(event.attributes["concept:name"]?.value in eventNames)
                assertTrue(event.attributes["lifecycle:transition"]?.value in lifecyleTransitions)
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectAggregationTest() {
        TODO("Add cost:total to traces")
        val stream = q("select min(t:total), avg(t:total), max(t:total) where l:id='$uuid'")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0.1, trace.attributes["min(t:cost:total)"]?.value)
        assertEquals(2.72, trace.attributes["avg(t:cost:total)"]?.value)
        assertEquals(3.14, trace.attributes["max(t:cost:total)"]?.value)

        assertEquals(0, trace.events.count())
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val stream = q("select [e:result], [e:time:timestamp], [org:concept:name] where l:id='$uuid'")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.identityId)
        assertNull(log.lifecycleModel)
        standardAndAllAttributesMatch(log, log)

        assertEquals(100, log.traces.count())
        for (trace in log.traces) {
            assertNull(trace.conceptName)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

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
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectExpressionTest() {
        val stream = q(
            "select [e:concept:name] + e:resource, max(timestamp) - \t \n min(timestamp)" +
                    "group event by [e:concept:name], e:resource"
        )
        TODO()
    }

    @Test
    fun selectAllImplicitTest() {
        val stream = q("")
        TODO()
    }

    @Test
    fun selectConstantsTest() {
        val stream = q("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9, 10 * null, t:null/11, l:D2020-03-12")
        TODO()
    }

    @Test
    fun selectISO8601Test() {
        val stream = q(
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
        TODO()
    }

    @Test
    fun selectIEEE754Test() {
        val stream = q(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE}"
        )
        TODO()
    }

    @Test
    fun selectBooleanTest() {
        val stream = q("select true, false")
        TODO()
    }

    @Test
    fun selectStringTest() {
        val stream = q("select 'single-quoted', \"double-quoted\"")
        TODO()
    }

    @Test
    fun selectNowTest() {
        val stream = q("select now()")
        TODO()
    }

    @Test
    fun errorHandlingTest() {
        TODO()
    }

    @Test
    fun whereSimpleTest() {
        val stream = q("where dayofweek(e:timestamp) in (1, 7)")
        TODO()
    }

    @Test
    fun whereSimpleWithHoistingTest() {
        val stream = q("where dayofweek(^e:timestamp) in (1, 7)")
        TODO()
    }

    @Test
    fun whereSimpleWithHoistingTest2() {
        val stream = q("where dayofweek(^^e:timestamp) in (1, 7)")
        TODO()
    }

    @Test
    fun whereLogicExprWithHoistingTest() {
        val stream = q("where not(t:currency = ^e:currency)")
        TODO()
    }

    @Test
    fun whereLogicExprTest() {
        val stream = q("where t:currency != e:currency")
        TODO()
    }

    @Test
    fun whereLogicExpr2Test() {
        val stream = q("where not(t:currency = ^e:currency) and t:total is null")
        TODO()
    }

    @Test
    fun whereLogicExpr3Test() {
        val stream = q("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2020-01-01) and t:total is null")
        TODO()
    }

    @Test
    fun whereLikeAndMatchesTest() {
        val stream = q("where t:name like 'transaction %' and ^e:resource matches '^[A-Z][a-z]+ [A-Z][a-z]+$'")
        TODO()
    }

    @Test
    fun groupScopeByClassifierTest() {
        val stream = q("group trace by e:classifier:activity")
        TODO()
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val stream = q(
            """select t:name, e:name, sum(e:total)
            group event by e:name"""
        )
        TODO()
    }

    @Test
    fun groupLogByEventStandardAttributeTest() {
        val stream = q(
            """select e:name, sum(e:total)
            group log by e:name"""
        )
        TODO()
    }

    @Test
    fun groupByImplicitScopeTest() {
        val stream = q("group by e:c:main, [t:branch]")
        TODO()
    }

    @Test
    fun groupByMeaninglessScopeTest() {
        val stream = q("group trace by l:name")
        TODO()
    }

    @Test
    fun groupByImplicitFromSelectTest() {
        val stream = q("select avg(e:total), min(e:timestamp), max(e:timestamp)")
        TODO()
    }

    @Test
    fun groupByImplitFromOrderByTest() {
        val stream = q("order by avg(e:total), min(e:timestamp), max(e:timestamp)")
        TODO()
    }

    @Test
    fun groupByImplicitWithHoistingTest() {
        val stream = q("select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp)")
        TODO()
    }

    @Test
    fun orderBySimpleTest() {
        val stream = q("order by e:timestamp")
        TODO()
    }

    @Test
    fun orderByWithModifierAndScopesTest() {
        val stream = q("order by t:total desc, e:timestamp")
        TODO()
    }

    @Test
    fun orderByWithModifierAndScopes2Test() {
        val stream = q("order by e:timestamp, t:total desc")
        TODO()
    }

    @Test
    fun orderByExpressionTest() {
        val stream = q("group trace by e:name order by min(^e:timestamp)")
        TODO()
    }

    @Test
    fun orderByExpression2Test() {
        val stream = q("group trace by e:name order by [l:basePrice] * avg(^e:total) * 3.141592 desc")
        TODO()
    }

    @Test
    fun limitSingleTest() {
        val stream = q("limit l:1")
        TODO()
    }

    @Test
    fun limitAllTest() {
        val stream = q("limit e:3, t:2, l:1")
        TODO()
    }

    @Test
    fun offsetSingleTest() {
        val stream = q("offset l:1")
        TODO()
    }

    @Test
    fun offsetAllTest() {
        val stream = q("offset e:3, t:2, l:1")
        TODO()
    }

    private fun q(query: String): DBHierarchicalXESInputStream = DBHierarchicalXESInputStream(Query(query))

    private fun standardAndAllAttributesMatch(log: Log, element: XESElement) {
        val nameMap = getStandardToCustomNameMap(log)

        // Ignore comparison if there is no value in element.attributes.
        // This is because XESInputStream implementations are required to only map custom attributes to standard attributes
        // but not otherwise.
        fun cmp(standard: Any?, standardName: String) =
            assertTrue(standard == element.attributes[nameMap[standardName]]?.value || element.attributes[nameMap[standardName]]?.value == null)

        cmp(element.conceptName, "concept:name")
        cmp(element.identityId, "identity:id")

        when (element) {
            is Log -> {
                cmp(element.lifecycleModel, "lifecycle:model")
            }
            is Trace -> {
                cmp(element.costCurrency, "cost:currency")
                cmp(element.costTotal, "cost:total")
            }
            is Event -> {
                cmp(element.conceptInstance, "concept:instance")
                cmp(element.costCurrency, "cost:currency")
                cmp(element.costTotal, "cost:total")
                cmp(element.lifecycleTransition, "lifecycle:transition")
                cmp(element.lifecycleState, "lifecycle:state")
                cmp(element.orgGroup, "org:group")
                cmp(element.orgResource, "org:resource")
                cmp(element.orgRole, "org:role")
                cmp(element.timeTimestamp, "time:timestamp")
            }
        }

    }

    private val nameMapCache: IdentityHashMap<Log, Map<String, String>> = IdentityHashMap()
    private fun getStandardToCustomNameMap(log: Log): Map<String, String> = nameMapCache.computeIfAbsent(log) {
        it.extensions.values.getStandardToCustomNameMap()
    }
}