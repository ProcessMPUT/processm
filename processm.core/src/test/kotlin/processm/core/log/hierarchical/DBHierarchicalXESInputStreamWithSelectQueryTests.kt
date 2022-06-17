package processm.core.log.hierarchical

import org.junit.jupiter.api.assertThrows
import processm.core.helpers.implies
import processm.core.helpers.parseISO8601
import processm.core.helpers.toDateTime
import processm.core.log.attribute.StringAttr
import processm.core.log.attribute.value
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlin.test.*

class DBHierarchicalXESInputStreamWithSelectQueryTests : DBHierarchicalXESInputStreamWithQueryTestsBase() {
    @Test
    fun basicSelectTest() {
        var _stream: DBHierarchicalXESInputStream? = null

        measureTimeMillis {
            _stream = q("select l:name, t:name, e:name, e:timestamp where l:name='JournalReview' and l:id=$journal")
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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecycleTransitions)
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
        val stream = q("select t:*, e:*, l:* where l:concept:name like 'Jour%Rev%' and l:id=$journal")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertEquals(journal, log.identityId)
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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecycleTransitions)
                assertNull(event.orgGroup)
                assertTrue(event.orgResource in orgResources)
                assertNull(event.orgRole)
                assertNull(event.identityId)
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun scopedSelectAllWithGroupBy() {
        assertThrows<IllegalArgumentException> {
            q("select l:*, t:*, e:*, max(e:timestamp)-min(e:timestamp) where l:name like 'Jour%Rev%' and l:id=$journal group by e:instance")
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
                assertTrue(event.lifecycleTransition in lifecycleTransitions)
                assertNull(event.orgGroup)
                assertNull(event.orgResource)
                assertNull(event.orgRole)
                assertNull(event.identityId)

                assertTrue(event.attributes["concept:name"]?.value in eventNames)
                assertTrue(event.attributes["lifecycle:transition"]?.value in lifecycleTransitions)
                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectAggregationTest() {
        val stream = q("select min(t:total), avg(t:total), max(t:total) where l:id=$journal")

        assertEquals(1, stream.count()) // only one log
        val log = stream.first()

        assertEquals(1, log.traces.count())
        val trace = log.traces.first()
        assertEquals(101, trace.count)
        assertEquals(11.0, trace.attributes["min(trace:cost:total)"]?.value)
        assertEquals(21.98, trace.attributes["avg(trace:cost:total)"]?.value)
        assertEquals(47.0, trace.attributes["max(trace:cost:total)"]?.value)

        assertTrue(trace.events.count() > 1)
        assertEquals(2298, trace.events.sumBy { it.count })
    }

    @Test
    fun selectNonStandardAttributesTest() {
        val stream = q("select [e:result], [e:time:timestamp], [e:concept:name] where l:id=$journal")

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
                    "where l:id=$journal " +
                    "group by [e:concept:name], e:resource"
        )
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(0, log.attributes.size)
        assertEquals(101, log.traces.count())

        for (trace in log.traces) {
            assertEquals(0, trace.attributes.size)
            assertTrue(trace.events.count() > 0)

            val groups = trace.events.map { it.attributes["[event:concept:name] + event:org:resource"] }.toList()
            assertEquals(groups.distinct().size, groups.size)

            for (event in trace.events) {
                val rangeInDays =
                    event.attributes["max(event:time:timestamp) - min(event:time:timestamp)"]!!.value as Double
                val count = event.attributes["count(event:time:timestamp)"]!!.value as Long
                assertTrue({ count == 1L } implies { rangeInDays < 1e-6 })
                assertTrue({ count != 1L } implies { rangeInDays >= 0.0 })
            }
        }
    }

    @Test
    fun selectComplexScalarExpressionTest() {
        val stream = q("select e:total + t:total where l:id=$journal")
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(1, log.count)
        assertEquals(101, log.traces.count())

        assertTrue(log.traces.any { t -> t.events.any { e -> e["event:cost:total + trace:cost:total"] !== null } })

        for (trace in log.traces) {
            assertEquals(1, trace.count)
            assertTrue(trace.events.count() >= 1)
            for (event in trace.events) {
                val cost = event["event:cost:total + trace:cost:total"]
                val minCost = (trace.events.count() + 1).toDouble()
                assertTrue(cost === null || cost is Double && cost in minCost..(minCost + 0.08))
            }
        }
    }

    @Test
    fun selectAllImplicitTest() {
        val stream = q("")
        assertTrue(stream.count() >= 1) // at least one log
        val log = stream.first { it.identityId == journal } // filter in the application layer

        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertEquals(journal, log.identityId)
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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertTrue(event.costCurrency in validCurrencies)
                assertTrue(event.costTotal!! in 1.0..1.08)
                assertNull(event.lifecycleState)
                assertTrue(event.lifecycleTransition in lifecycleTransitions)
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
        val now = Instant.now()
        val stream = q("select l:now() limit l:1")
        Thread.sleep(10)
        val now2 = Instant.now()

        val diff = Duration.between(now, now2).toMillis()
        assertTrue(
            diff > 0L,
            "This test requires that the time difference between obtaining now and now2 values is nonzero. Was: ${diff}ms."
        )

        // verify whether lazy-evaluation does not affect the timestamp
        Thread.sleep(diff + 1L)
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(1, log.attributes.size)
        assertTrue(abs(Duration.between(now, log.attributes["log:now()"]?.value as Instant?).toMillis()) <= diff)

        // verify repetitiveness
        Thread.sleep(1L)
        val log2 = stream.first()
        assertEquals(log.attributes["log:now()"]?.value, log2.attributes["log:now()"]?.value)
    }

    @Test
    fun selectSeconds() {
        val stream = q("select e:*, second(e:timestamp) where l:id=$journal")
        val log = stream.first()
        for (trace in log.traces) {
            for (event in trace.events) {
                assertEquals(
                    event.timeTimestamp!!.toDateTime().second.toDouble(),
                    event.attributes["second(event:time:timestamp)"]?.value
                )
            }
        }
    }

    @Test
    fun nonexistentCustomAttributesTest() {
        val nonexistentAttributes = listOf(
            "select [🦠] where l:id=$journal",
            "select [result] where l:id=$journal" // exists in some events
        )

        val validResults = mutableMapOf(null to 0, "accept" to 0, "reject" to 0)

        for (query in nonexistentAttributes) {
            val stream = q(query)
            assertEquals(1, stream.count())

            val log = stream.first()
            for (trace in log.traces) {
                for (event in trace.events) {
                    assertNull(event.attributes["🦠"])

                    val result = event.attributes["result"]?.value as String?
                    assertTrue(result in validResults.keys)
                    validResults.compute(result) { _, v -> v!! + 1 }
                }
            }
        }

        assertTrue(validResults[null]!! > 0)
        assertTrue(validResults["accept"]!! > 0)
        assertTrue(validResults["reject"]!! > 0)

        // the result attribute is used in reviews only
        val zeroMatch = q("where l:id=$journal and e:name=[result]")
        assertEquals(0, zeroMatch.count())
    }
}
