package processm.core.log.hierarchical

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import processm.core.helpers.implies
import processm.core.helpers.mapToSet
import processm.core.helpers.parseISO8601
import processm.core.helpers.toDateTime
import processm.core.log.*
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.StringAttr
import processm.core.log.attribute.value
import processm.core.logging.logger
import processm.core.persistence.DBConnectionPool
import processm.core.querylanguage.Query
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.abs
import kotlin.math.max
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
        private val begin = "2005-12-31T00:00:00.000Z".parseISO8601()
        private val end = "2008-05-05T00:00:00.000Z".parseISO8601()
        private val validCurrencies = setOf("EUR", "USD")

        @BeforeAll
        @JvmStatic
        fun setUp() {
            try {
                logger.info("Loading data")
                measureTimeMillis {
                    val stream = this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes")
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
            } catch (e: Throwable) {
                e.printStackTrace()
                throw e
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

        assertEquals(101, log.traces.count())
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
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
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertNull(trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() == trace.events.count())
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() == trace.events.count())
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
        val stream = q("select min(t:total), avg(t:total), max(t:total) where l:id='$uuid'")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(1, trace.attributes["min(t:cost:total)"]?.value)
        assertEquals(10, trace.attributes["avg(t:cost:total)"]?.value)
        assertEquals(52, trace.attributes["max(t:cost:total)"]?.value)

        assertEquals(0, trace.events.count())
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val stream = q("select [e:result], [e:time:timestamp], [e:concept:name] where l:id='$uuid'")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.identityId)
        assertNull(log.lifecycleModel)
        standardAndAllAttributesMatch(log, log)

        assertEquals(101, log.traces.count())
        assertTrue(log.traces.any { trace ->
            trace.events.map { it.attributes["result"] }.filterNotNull().any()
        })
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
                assertTrue(with(event.attributes["result"]) { this === null || this is StringAttr && this.value in results })
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectExpressionTest() {
        val stream = q(
            "select [e:concept:name] + e:resource, max(timestamp) - \t \n min(timestamp), count(e:time:timestamp) " +
                    "where l:id='$uuid' " +
                    "group event by [e:concept:name], e:resource"
        )
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)
        assertEquals(101, log.traces.count())

        for (trace in log.traces) {
            assertEquals(0, trace.attributes.size)
            assertTrue(trace.events.count() > 0)

            val groups = trace.events.map { it.attributes["concept:name+org:resource"] }.toList()
            assertEquals(groups.distinct().size, groups.size)

            for (event in trace.events) {
                val rangeInDays = event.attributes["max(time:timestamp)-min(time:timestamp)"]!!.value as Double
                val count = event.attributes["count(time:timestamp)"]!!.value as Double
                assertTrue({ abs(count - 1.0) < 1e-6 } implies { rangeInDays < 1e-6 })
                assertTrue({ abs(count - 1.0) > 1e-6 } implies { rangeInDays > 1e-6 })
            }
        }
    }

    @Test
    fun selectAllImplicitTest() {
        val stream = q("")
        assertTrue(stream.count() >= 1) // at least one log
        val log = stream.first { it.identityId == uuid.toString() } // filter in the application layer

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
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() == trace.events.count())
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end))
                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
    fun selectConstantsTest() {
        val stream =
            q("select l:1, l:2 + t:3, l:4 * t:5 + e:6, 7 / 8 - 9, 10 * null, t:null/11, l:D2020-03-12 limit l:1, t:1, e:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(2, log.attributes.size)
        assertEquals(1.0, log.attributes["log:1.0"]?.value)
        assertEquals("2020-03-12T00:00:00Z".parseISO8601(), log.attributes["log:D2020-03-12T00:00:00Z"]?.value)

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(2, trace.attributes.size)
        assertEquals(5.0, trace.attributes["log:2.0 + trace:3.0"]?.value)
        assertNull(trace.attributes["trace:null / 11.0"]!!.value)

        assertEquals(1, trace.events.count())
        val event = trace.events.first()
        assertEquals(3, event.attributes.size)
        assertEquals(26.0, event.attributes["log:4.0 * trace:5.0 + event:6.0"]?.value)
        assertEquals(-8.125, event.attributes["7.0 / 8.0 - 9.0"]?.value)
        assertNull(event.attributes["10.0 * null"]!!.value)
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
                    limit l:1, t:1, e:1
                    """
        )
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0, trace.attributes.size)

        assertEquals(1, trace.events.count())
        val event = trace.events.first()
        assertEquals(5, event.attributes.size)
        assertEquals("2020-03-13T00:00:00Z".parseISO8601(), event.attributes["D2020-03-13T00:00:00Z"]?.value)
        assertEquals("2020-03-13T16:45:00Z".parseISO8601(), event.attributes["D2020-03-13T16:45:00Z"]?.value)
        assertEquals("2020-03-13T16:45:50Z".parseISO8601(), event.attributes["D2020-03-13T16:45:50Z"]?.value)
        assertEquals("2020-03-13T16:45:50.333Z".parseISO8601(), event.attributes["D2020-03-13T16:45:50.333Z"]?.value)
        assertEquals("2020-03-13T14:45:00Z".parseISO8601(), event.attributes["D2020-03-13T14:45:00Z"]?.value)
        // TODO: should we return duplicate attributes?
    }

    @Test
    fun selectIEEE754Test() {
        val stream = q(
            "select 0, 0.0, 0.00, -0, -0.0, 1, 1.0, -1, -1.0, ${Math.PI}, ${Double.MIN_VALUE}, ${Double.MAX_VALUE} limit l:1, t:1, e:1"
        )
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0, trace.attributes.size)

        assertEquals(1, trace.events.count())
        val event = trace.events.first()
        assertEquals(7, event.attributes.size)
        assertEquals(0.0, event.attributes["0.0"]?.value)
        assertEquals(-0.0, event.attributes["-0.0"]?.value)
        assertEquals(1.0, event.attributes["1.0"]?.value)
        assertEquals(-1.0, event.attributes["-1.0"]?.value)
        assertEquals(Math.PI, event.attributes["${Math.PI}"]?.value)
        assertEquals(Double.MIN_VALUE, event.attributes["${Double.MIN_VALUE}"]?.value)
        assertEquals(Double.MAX_VALUE, event.attributes["${Double.MAX_VALUE}"]?.value)
        // TODO: should we return duplicate attributes?
    }

    @Test
    fun selectBooleanTest() {
        val stream = q("select true, false limit l:1, t:1, e:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0, trace.attributes.size)

        assertEquals(1, trace.events.count())
        val event = trace.events.first()
        assertEquals(2, event.attributes.size)
        assertEquals(true, event.attributes["true"]?.value)
        assertEquals(false, event.attributes["false"]?.value)
    }

    @Test
    fun selectStringTest() {
        val stream = q("select 'single-quoted', \"double-quoted\" limit l:1, t:1, e:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(0, trace.attributes.size)

        assertEquals(1, trace.events.count())
        val event = trace.events.first()
        assertEquals(2, event.attributes.size)
        assertEquals("single-quoted", event.attributes["single-quoted"]?.value)
        assertEquals("double-quoted", event.attributes["double-quoted"]?.value)
    }

    @Test
    fun selectNowTest() {
        val stream = q("select l:now() limit l:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(1, log.attributes.size)
        assertTrue(abs(Duration.between(Instant.now(), log.attributes["log:now()"]?.value as Instant?).seconds) <= 1)
    }

    @Test
    fun errorHandlingTest() {
        TODO()
        // select non-existent non-standard attribute
        // duplicated attributes/expressions
    }

    @Test
    fun whereSimpleTest() {
        val stream = q("where dayofweek(e:timestamp) in (1, 7) and l:id='$uuid'")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!! > 0.0)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())

                assertTrue(event.timeTimestamp!!.toDateTime().dayOfWeek in validDays, event.timeTimestamp?.toString())

                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
    fun whereSimpleWithHoistingTest() {
        val stream = q("where dayofweek(^e:timestamp) in (1, 7) and l:id='$uuid'")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)
        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() == trace.events.count())
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)

            assertTrue(trace.events.any { it.timeTimestamp!!.toDateTime().dayOfWeek in validDays })
            assertTrue(trace.events.any { it.timeTimestamp!!.toDateTime().dayOfWeek !in validDays })

            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
    fun whereSimpleWithHoistingTest2() {
        val stream = q("where dayofweek(^^e:timestamp) in (1, 7) and l:id='$uuid'")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        assertTrue(log.traces.any { t -> t.events.any { e -> e.timeTimestamp!!.toDateTime().dayOfWeek in validDays } })
        assertTrue(log.traces.any { t -> t.events.any { e -> e.timeTimestamp!!.toDateTime().dayOfWeek !in validDays } })
        assertTrue(log.traces.any { t -> t.events.none { e -> e.timeTimestamp!!.toDateTime().dayOfWeek in validDays } })

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!! > 0)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            assertTrue(trace.events.any { it.timeTimestamp!!.toDateTime().dayOfWeek in validDays } || trace.conceptName == "-1")
            assertTrue(trace.events.any { it.timeTimestamp!!.toDateTime().dayOfWeek !in validDays })

            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
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
    fun whereLogicExprWithHoistingTest() {
        val stream = q("where not(t:currency = ^e:currency) and l:id='$uuid'")
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!! > 0)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExprTest() {
        val stream = q("where t:currency != e:currency and l:id='$uuid'")
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!! > 0)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertTrue(event.timeTimestamp!!.isAfter(begin))
                assertTrue(event.timeTimestamp!!.isBefore(end), event.timeTimestamp.toString())
                assertNull(event.conceptInstance)
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExpr2Test() {
        val stream = q("where not(t:currency = ^e:currency) and t:total is null and l:id='$uuid'")
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
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
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExpr3Test() {
        val stream =
            q("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2007-01-01) and t:total is null and l:id='$uuid'")
        val myBegin = "2007-01-01T00:00:00Z".parseISO8601()
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)
        assertTrue(log.traces.all { t -> t.costTotal === null })
        assertTrue(log.traces.all { t ->
            t.costCurrency !in t.events.mapToSet { e -> e.costCurrency }
                    || t.events.any { e -> !e.timeTimestamp!!.isBefore(myBegin) }
        })


        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() > 0)
        }
    }

    @Test
    fun whereLikeAndMatchesTest() {
        val stream =
            q("where t:name like '%5' and ^e:resource matches '^[SP]am$' and l:id='$uuid'")
        val nameRegex = Regex("^[SP]am$")
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        for (trace in log.traces) {
            assertTrue(trace.conceptName!!.endsWith("5"))
            assertTrue(trace.events.any { e -> nameRegex.matches(e.orgResource!!) })
            assertTrue(trace.events.any { e -> !nameRegex.matches(e.orgResource!!) })
        }
    }

    @Test
    fun groupScopeByClassifierTest() {
        val stream = q("where l:id='$uuid' group trace by e:classifier:activity")
        TODO()
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val stream = q("select t:name, e:name, sum(e:total) where l:id='$uuid' group event by e:name")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(101, log.traces.count())

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName in -1..100)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertTrue(trace.events.count() >= 1)

            val distinctConceptNames = trace.events.distinctBy { it.conceptName }.count()
            assertEquals(distinctConceptNames, trace.events.count())

            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)

                assertTrue((event.attributes["sum(event:cost:total)"] as RealAttr).value >= 1.0)
            }
        }
    }

    @Test
    fun groupLogByEventStandardAttributeTest() {
        val stream = q("select e:name, sum(e:total) group log by e:name")
        assertTrue(stream.count() >= 1)

        val log = stream.first { it.conceptName == "JournalReview" }

    }

    @Test
    fun groupByImplicitScopeTest() {
        val stream = q("group by e:c:main, [t:branch]")
        TODO()
    }

    @Test
    fun groupByOuterScopeTest() {
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
        val stream = q("where l:name='JournalReview' order by e:timestamp limit l:3")
        assertTrue(stream.count() > 0)
        assertTrue(stream.count() <= 3)
        for (log in stream) {
            assertTrue(log.traces.count() > 0)
            assertTrue(log.traces.count() <= 101)
            for (trace in log.traces) {
                assertTrue(trace.events.count() > 0)
                assertTrue(trace.events.count() <= 55)

                var lastTimestamp = begin
                for (event in trace.events) {
                    assertTrue(!event.timeTimestamp!!.isBefore(lastTimestamp))
                    lastTimestamp = event.timeTimestamp!!
                }
            }
        }
    }

    @Test
    fun orderByWithModifierAndScopesTest() {
        val stream = q("where l:name='JournalReview' order by t:total desc, e:timestamp limit l:3")
        for (log in stream) {
            assertTrue(log.traces.count() == 101)

            var lastTotal: Double? = null // nulls first
            for (trace in log.traces) {
                assertTrue(cmp(trace.costTotal, lastTotal) <= 0)
                lastTotal = trace.costTotal

                assertTrue(trace.events.count() <= 55)
                var lastTimestamp = begin
                for (event in trace.events) {
                    assertTrue(!event.timeTimestamp!!.isBefore(lastTimestamp))
                    lastTimestamp = event.timeTimestamp!!
                }
            }
        }
    }

    @Test
    fun orderByWithModifierAndScopes2Test() {
        val stream = q("where l:name='JournalReview' order by e:timestamp, t:total desc limit l:3")
        for (log in stream) {
            assertTrue(log.traces.count() == 101)

            var lastTotal: Double? = null // nulls first
            for (trace in log.traces) {
                assertTrue(cmp(trace.costTotal, lastTotal) <= 0)
                lastTotal = trace.costTotal

                assertTrue(trace.events.count() <= 55)
                var lastTimestamp = begin
                for (event in trace.events) {
                    assertTrue(!event.timeTimestamp!!.isBefore(lastTimestamp), "${event.timeTimestamp} $lastTimestamp")
                    lastTimestamp = event.timeTimestamp!!
                }
            }
        }
    }

    private fun <T : Comparable<T>> cmp(a: T?, b: T?): Int {
        if (a === b)
            return 0 // for nulls and the same object supplied twice
        if (a === null)
            return 1
        if (b === null)
            return -1
        return a.compareTo(b)
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
        val stream = q("where l:name='JournalReview' limit l:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertTrue(log.traces.count() > 1)
        assertTrue(log.traces.any { t -> t.events.count() > 1 })
    }

    @Test
    fun limitAllTest() {
        val stream = q("limit e:3, t:2, l:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertTrue(log.traces.count() <= 2)
        assertTrue(log.traces.all { t -> t.events.count() <= 3 })
    }

    @Test
    fun offsetSingleTest() {
        val stream = q("where l:id='$uuid' offset l:1")
        assertEquals(0, stream.count())

        val journalAll = q("where l:name like 'Journal%'")
        val journalWithOffset = q("where l:name like 'Journal%' offset l:1")
        assertEquals(max(journalAll.count() - 1, 0), journalWithOffset.count())
    }

    @Test
    fun offsetAllTest() {
        val stream = q("where l:id='$uuid' offset e:3, t:2, l:1")
        assertEquals(0, stream.count())

        val journalAll = q("where l:name='JournalReview' limit l:3").map { it.identityId to it }.toMap()
        val journalWithOffset = q("where l:name='JournalReview' limit l:3 offset e:3, t:2")
        assertEquals(journalAll.size, journalWithOffset.count())

        for (log in journalWithOffset) {
            val logFromAll = journalAll[log.identityId]!!
            val tracesFromAll = logFromAll.traces.map { it.conceptName to it }.toMap()
            assertEquals(max(tracesFromAll.size - 2, 0), log.traces.count())

            for (trace in log.traces) {
                val traceFromAll = tracesFromAll[trace.conceptName]!!
                assertEquals(max(traceFromAll.events.count() - 3, 0), trace.events.count())
            }
        }
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