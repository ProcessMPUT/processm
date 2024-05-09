package processm.core.log.hierarchical

import processm.core.DBTestHelper.dbName
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.log.attribute.AttributeMap.Companion.STRING_MARKER
import processm.core.querylanguage.PQLSyntaxError
import processm.core.querylanguage.Query
import java.time.Instant
import kotlin.math.max
import kotlin.test.*

class DBHierarchicalXESInputStreamWithQueryTests : DBHierarchicalXESInputStreamWithQueryTestsBase() {
    @Test
    fun errorHandlingTest() {
        val nonexistentClassifiers = listOf(
            "order by c:nonexistent",
            "group by [^c:nonstandard nonexisting]"
        )

        for (query in nonexistentClassifiers) {
            val ex = assertFailsWith<IllegalArgumentException> {
                val log = q(query).first()
                val trace = log.traces.first()
                trace.events.first() // throws exception
            }
            assertTrue("not found" in ex.message!!)
            assertTrue(ex.message!!.contains("classifier", true))
        }
    }

    @Test
    fun invalidUseOfClassifiers() {
        assertFailsWith<PQLSyntaxError> {
            val log =
                q("where [e:classifier:concept:name+lifecycle:transition] in ('acceptcomplete', 'rejectcomplete') and l:id=$journal").first()
            val trace = log.traces.first()
            trace.events.first() // exception is thrown here
        }.apply {
            assertEquals(PQLSyntaxError.Problem.ClassifierInWhere, problem)
        }

        val validUse = q("select [e:c:Event Name] where l:id=$journal")
        assertEquals(1, validUse.count())
        assertEquals(2298L, validUse.readVersion())

        val log = validUse.first()
        assertEquals(101, log.traces.count())
        for (trace in log.traces) {
            assertTrue(trace.events.count() >= 1)
            for (event in trace.events) {
                assertEquals(1, event.attributes.count())
                assertTrue(event.conceptName in eventNames)
            }
        }
    }

    @Test
    fun duplicateAttributes() {
        val stream = q("select e:name, [e:c:Event Name] where l:id=$journal")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertEquals(101, log.traces.count())
        for (trace in log.traces) {
            assertTrue(trace.events.count() >= 1)
            for (event in trace.events) {
                assertTrue(event.conceptName in eventNames)
                assertEquals(1, event.attributes.count())
                assertEquals(CONCEPT_NAME, event.attributes.keys.first())

                standardAndAllAttributesMatch(log, event)
            }
        }
    }

    @Test
    fun selectEmpty() {
        val stream = q("where 0=1")
        assertEquals(0, stream.count())
        assertEquals(0L, stream.readVersion())
    }

    @Test
    fun groupScopeByClassifierTest() {
        val stream =
            q("select [e:classifier:concept:name+lifecycle:transition] where l:id=$journal group by [^e:classifier:concept:name+lifecycle:transition]")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        // as of 2020-06-04 JournalReview-extra.xes consists of variants (w.r.t. concept:name+lifecycle:transition):
        // | variant id | trace count | variant
        // |          1 |           3 | inv inv get get get col col dec dec inv inv get rej rej
        // |          2 |           2 | inv inv get get get col col dec dec acc acc
        // |          3 |           2 | inv inv get get tim col col dec dec inv inv tim inv inv tim inv inv get acc acc
        // |     4 - 97 |           1 | n/a

        val variant1 =
            sequenceOf("inv", "inv", "get", "get", "get", "col", "col", "dec", "dec", "inv", "inv", "get", "rej", "rej")
        val variant2 = sequenceOf("inv", "inv", "get", "get", "get", "col", "col", "dec", "dec", "acc", "acc")
        val variant3 =
            sequenceOf(
                "inv", "inv", "get", "get", "tim", "col", "col", "dec", "dec", "inv", "inv", "tim", "inv",
                "inv", "tim", "inv", "inv", "get", "acc", "acc"
            )

        assertEquals(97, log.traces.count())

        assertEquals(1, log.traces.count { it.count == 3 })
        assertEquals(2, log.traces.count { it.count == 2 })
        assertEquals(94, log.traces.count { it.count == 1 })

        assertTrue(
            log.traces
                .first { it.count == 3 }.events
                .map { it.conceptName!! }
                .zip(variant1)
                .all { (act, exp) -> act.startsWith(exp) }
        )

        assertTrue(
            log.traces
                .filter { it.count == 2 }.any {
                    it.events
                        .map { it.conceptName!! }
                        .zip(variant2)
                        .all { (act, exp) -> act.startsWith(exp) }
                }
        )

        assertTrue(
            log.traces
                .filter { it.count == 2 }.any {
                    it.events
                        .map { it.conceptName!! }
                        .zip(variant3)
                        .all { (act, exp) -> act.startsWith(exp) }
                }
        )

        for (trace in log.traces) {
            assertTrue(trace.events.count() > 0)
        }
    }

    @Test
    fun groupEventByStandardAttributeTest() {
        val stream = q("select t:name, e:name, sum(e:total) where l:id=$journal group by e:name")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

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

                assertTrue(event.attributes["sum(event:cost:total)"] as Double >= 1.0)
            }
        }
    }

    @Test
    fun groupLogByEventStandardAttributeAndImplicitGroupEventByTest() {
        val stream = q("select sum(e:total) where l:name='JournalReview' group by ^^e:name")
        assertTrue(stream.count() == 1)
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertTrue(log.count >= 1)

        assertTrue(log.traces.count() > 1)
        assertEquals(101, log.traces.sumBy { it.count } / log.count)

        for (trace in log.traces) {
            assertEquals(1, trace.events.count())

            val event = trace.events.first()
            assertTrue(event.count >= 1)
            assertTrue((event.attributes["sum(event:cost:total)"] as Double) in (1.0 * event.count)..(1.08 * event.count + 1e-6))
        }
    }

    @Test
    fun groupLogByEventStandardAndGroupEventByStandardAttributeAttributeTest() {
        val stream = q("select e:name, sum(e:total) where l:name='JournalReview' group by ^^e:name, e:name")
        assertTrue(stream.count() == 1)
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertTrue(log.count >= 1)

        assertTrue(log.traces.count() > 1)
        assertEquals(101, log.traces.sumBy { it.count } / log.count)

        for (trace in log.traces) {
            assertTrue(trace.events.count() >= 1)

            for (event in trace.events) {
                assertTrue(event.count >= 1)
                assertTrue((event.attributes["sum(event:cost:total)"] as Double) in (1.0 * event.count)..(1.08 * event.count))
            }
        }
    }

    @Test
    fun groupByImplicitScopeTest() {
        val stream = q("where l:id=$journal group by c:Resource")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertEquals(101, log.traces.count())
        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertEquals(journal, log.identityId)
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() in 1..50)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() >= 1)
            for (event in trace.events) {
                assertTrue(event.count >= 1)
                assertNull(event.conceptName)
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.orgGroup)
                assertNull(event.orgRole)
                assertTrue(event.orgResource in orgResources)
                assertNull(event.timeTimestamp)
                assertEquals(1, event.attributes.size)
                assertTrue(event.attributes["org:resource"] in orgResources)
            }
        }
    }

    @Test
    fun groupByOuterScopeTest() {
        val stream = q("select t:min(l:name) where l:name='JournalReview' limit l:3")
        assertTrue(stream.count() in 1..3)
        assertEquals(2298L, stream.readVersion())

        for (log in stream) {
            assertEquals(1, log.traces.count())

            val trace = log.traces.first()
            assertEquals(1, trace.attributes.size)
            assertEquals("JournalReview", trace.attributes["trace:min(log:concept:name)"])
        }
    }

    @Test
    fun groupByImplicitFromSelectTest() {
        val stream = q(
            "select l:*, t:*, avg(e:total), min(e:timestamp), max(e:timestamp) where l:name matches '(?i)^journalreview$' limit l:1"
        )
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertEquals(101, log.traces.count())
        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertNotNull(log.identityId)
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() in 1..50)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertEquals(1, trace.events.count())
            val event = trace.events.first()
            assertTrue(event.count >= 1)
            assertNull(event.conceptName)
            assertNull(event.conceptInstance)
            assertNull(event.costCurrency)
            assertNull(event.costTotal)
            assertNull(event.orgGroup)
            assertNull(event.orgRole)
            assertNull(event.orgResource)
            assertNull(event.timeTimestamp)
            assertEquals(3, event.attributes.size)
            assertTrue((event.attributes["avg(event:cost:total)"] as Double) in 1.0..1.08)
            assertTrue(begin.isBefore(event.attributes["min(event:time:timestamp)"] as Instant))
            assertTrue(end.isAfter(event.attributes["max(event:time:timestamp)"] as Instant))
        }
    }

    @Test
    fun groupByImplicitFromOrderByTest() {
        val stream = q("where l:id=$journal order by avg(e:total), min(e:timestamp), max(e:timestamp)")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertEquals(101, log.traces.count())
        assertEquals("JournalReview", log.conceptName)
        assertEquals("standard", log.lifecycleModel)
        assertEquals(journal, log.identityId)
        standardAndAllAttributesMatch(log, log)

        for (trace in log.traces) {
            val conceptName = Integer.parseInt(trace.conceptName)
            assertTrue(conceptName >= -1)
            assertTrue(conceptName <= 100)
            assertEquals("EUR", trace.costCurrency)
            assertTrue(trace.costTotal === null || trace.costTotal!!.toInt() in 1..50)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertEquals(1, trace.events.count())
            val event = trace.events.first()
            assertTrue(event.count >= 1)
            assertNull(event.conceptName)
            assertNull(event.conceptInstance)
            assertNull(event.costCurrency)
            assertNull(event.costTotal)
            assertNull(event.orgGroup)
            assertNull(event.orgRole)
            assertNull(event.orgResource)
            assertNull(event.timeTimestamp)
            assertEquals(0, event.attributes.size)
        }
    }

    @Test
    fun groupByImplicitWithHoistingTest() {
        val stream = q("select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp) where l:id=$journal")
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertNull(log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        assertTrue((log.attributes["avg(^^event:cost:total)"] as Double) in 1.0..1.08)
        assertTrue(begin.isBefore(log.attributes["min(^^event:time:timestamp)"] as Instant))
        assertTrue(end.isAfter(log.attributes["max(^^event:time:timestamp)"] as Instant))
        standardAndAllAttributesMatch(log, log)

        assertEquals(101, log.traces.count())
        for (trace in log.traces) {
            assertNull(trace.conceptName)
            assertNull(trace.costCurrency)
            assertNull(trace.costTotal)
            assertNull(trace.identityId)
            assertFalse(trace.isEventStream)
            standardAndAllAttributesMatch(log, trace)

            assertTrue(trace.events.count() >= 1)
            for (event in trace.events) {
                assertTrue(event.count >= 1)
                assertNull(event.conceptName)
                assertNull(event.conceptInstance)
                assertNull(event.costCurrency)
                assertNull(event.costTotal)
                assertNull(event.orgGroup)
                assertNull(event.orgRole)
                assertNull(event.orgResource)
                assertNull(event.timeTimestamp)
                assertEquals(0, event.attributes.size)
            }
        }
    }

    @Test
    fun orderBySimpleTest() {
        val stream = q("where l:name='JournalReview' order by e:timestamp limit l:3")
        assertEquals(2298L, stream.readVersion())
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
        assertEquals(2298L, stream.readVersion())
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
        assertEquals(2298L, stream.readVersion())
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
        val stream = q("select min(timestamp) where l:id=$journal group by ^e:name order by min(^e:timestamp)")
        assertEquals(2298L, stream.readVersion())
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals(97, log.traces.count())

        var lastTimestamp = begin
        for (trace in log.traces) {
            val minTimestamp =
                trace.events.minOfOrNull { it.attributes["min(event:time:timestamp)"] as Instant }!!
            assertTrue(!lastTimestamp.isAfter(minTimestamp))

            lastTimestamp = minTimestamp
        }
    }

    @Test
    fun groupByWithHoistingAndOrderByWithinGroupTest() {
        val stream = q("where l:id=$journal group by ^e:name order by name")
        assertEquals(2298L, stream.readVersion())
        val log = stream.first()

        // as of 2020-06-11 JournalReview-extra.xes consists of 78 variants w.r.t. concept:name order by concept:name:
        // | variant id | trace count | variant
        // |          1 |           4 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,invite reviewers,invite reviewers}
        // |          2 |           3 | {collect reviews,collect reviews,decide,decide,get review 1,get review 3,invite reviewers,invite reviewers,reject,reject,time-out 2}
        // |          3 |           3 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 3}
        // |          4 |           3 | {collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject}
        // |          5 |           2 | {collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1}
        // |          6 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1}
        // |          7 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 3,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1,time-out 2}
        // |          8 |           2 | {collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out X}
        // |          9 |           2 | {collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3}
        // |         10 |           2 | {collect reviews,collect reviews,decide,decide,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2}
        // |         11 |           2 | {collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3,time-out X,time-out X,time-out X}
        // |         12 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1,time-out 2}
        // |         13 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 3,time-out X,time-out X}
        // |         14 |           2 | {collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3,time-out X,time-out X,time-out X,time-out X}
        // |         15 |           2 | {collect reviews,collect reviews,decide,decide,get review X,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2,time-out 3,time-out X,time-out X,time-out X,time-out X}
        // |         16 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 3,invite reviewers,invite reviewers,time-out 2}
        // |         17 |           2 | {collect reviews,collect reviews,decide,decide,get review 3,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2,time-out X,time-out X}
        // |         18 |           2 | {accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out X,time-out X}
        // |    19 - 78 |           1 | n/a
        assertEquals(78, log.traces.count())

        val fourTraces = listOf(
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,invite reviewers,invite reviewers"
        ).map { it.split(',') }
        val threeTraces = listOf(
            "collect reviews,collect reviews,decide,decide,get review 1,get review 3,invite reviewers,invite reviewers,reject,reject,time-out 2",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 3",
            "collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject"
        ).map { it.split(',') }
        val twoTraces = listOf(
            "collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 3,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1,time-out 2",
            "collect reviews,collect reviews,decide,decide,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out X",
            "collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3",
            "collect reviews,collect reviews,decide,decide,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2",
            "collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3,time-out X,time-out X,time-out X",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 1,time-out 2",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out 3,time-out X,time-out X",
            "collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 3,time-out X,time-out X,time-out X,time-out X",
            "collect reviews,collect reviews,decide,decide,get review X,get review X,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2,time-out 3,time-out X,time-out X,time-out X,time-out X",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 3,invite reviewers,invite reviewers,time-out 2",
            "collect reviews,collect reviews,decide,decide,get review 3,get review X,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,reject,reject,time-out 1,time-out 2,time-out X,time-out X",
            "accept,accept,collect reviews,collect reviews,decide,decide,get review 1,get review 2,get review 3,get review X,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite additional reviewer,invite reviewers,invite reviewers,time-out X,time-out X"
        ).map { it.split(',') }

        fun validate(validTraces: List<List<String>>, count: Int) {
            for (validTrace in validTraces) {
                assertTrue(
                    log.traces.filter { it.count == count }.any {
                        it.events
                            .map { it.conceptName!! }
                            .zip(validTrace.asSequence())
                            .all { (act, exp) -> act == exp }
                    }
                )
            }
        }

        validate(fourTraces, 4)
        validate(threeTraces, 3)
        validate(twoTraces, 2)
    }

    /**
     * Demonstrates the bug from #105: PQL query does not group traces into variants.
     */
    @Test
    fun groupByWithHoistingAndOrderByCountTest() {
        // see [groupByWithHoistingAndOrderByWithinGroupTest]
        val stream = q(
            "select l:name, count(t:name), e:name\n" +
                    "where l:id=$journal\n" +
                    "group by ^e:name\n" +
                    "order by count(t:name) desc\n" +
                    "limit l:1\n"
        )
        assertEquals(1, stream.count())
        assertEquals(2298L, stream.readVersion())

        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        assertEquals("JournalReview", log.attributes[CONCEPT_NAME] as String)
        standardAndAllAttributesMatch(log, log)

        assertEquals(97, log.traces.count())

        for (trace in log.traces) {
            assertEquals(trace.attributes["count(trace:concept:name)"] as Long, trace.count.toLong())
        }

        for (trace in log.traces.drop(3)) {
            assertEquals(1, trace.count)
        }

        val threeTraces = listOf(
            "invite reviewers,invite reviewers,get review 2,get review 3,get review 1,collect reviews,collect reviews,decide,decide,invite additional reviewer,invite additional reviewer,get review X,reject,reject",
        ).map { it.split(',') }
        val twoTraces = listOf(
            "invite reviewers,invite reviewers,get review 2,get review 1,get review 3,collect reviews,collect reviews,decide,decide,accept,accept",
            "invite reviewers,invite reviewers,get review 2,get review 1,time-out 3,collect reviews,collect reviews,decide,decide,invite additional reviewer,invite additional reviewer,time-out X,invite additional reviewer,invite additional reviewer,time-out X,invite additional reviewer,invite additional reviewer,get review X,accept,accept",
        ).map { it.split(',') }

        fun validate(validTraces: List<List<String>>, logTraces: Sequence<Trace>, count: Int) {
            for (logTrace in logTraces) {
                assertEquals(count.toLong(), logTrace.attributes["count(trace:concept:name)"])
            }
            for (validTrace in validTraces) {
                assertTrue(
                    logTraces.any {
                        it.events
                            .map { it.conceptName!! }
                            .zip(validTrace.asSequence())
                            .all { (act, exp) -> act == exp }
                    }
                )
            }
        }

        validate(threeTraces, log.traces.take(1), 3)
        validate(twoTraces, log.traces.drop(1).take(2), 2)
    }

    /**
     * Demonstrates the bug #116 - aggregate function call on a hoisted attribute changes the values of the other
     * aggregate functions.
     */
    @Test
    fun aggregationFunctionIndependence() {
        // see [groupByWithHoistingAndOrderByCountTest]
        val stream1 = q(
            "select l:name, count(t:name), e:name\n" +
                    "where l:id=$journal\n" +
                    "group by ^e:name\n" +
                    "order by count(t:name) desc\n" +
                    "limit l:1\n"
        )
        assertEquals(2298L, stream1.readVersion())
        val stream2 = q(
            "select l:name, count(t:name), count(^e:name), e:name\n" +
                    "where l:id=$journal\n" +
                    "group by ^e:name\n" +
                    "order by count(t:name) desc\n" +
                    "limit l:1\n"
        )
        assertEquals(2298L, stream2.readVersion())
        assertEquals(stream1.count(), stream2.count())

        val log1 = stream1.first()
        val log2 = stream2.first()

        assertEquals(log1.traces.count(), log2.traces.count())
        for ((trace1, trace2) in log1.traces zip log2.traces) {
            assertEquals(
                trace1.attributes["count(trace:concept:name)"] as Long,
                trace2.attributes["count(trace:concept:name)"] as Long
            )
        }
    }

    /**
     * Demonstrates the bug from #106: PSQLException: ERROR: aggregate function calls cannot be nested
     */
    @Test
    fun groupByWithAndWithoutHoistingAndOrderByCountTest() {
        // see [groupByWithHoistingAndOrderByCountTest]
        val stream = q(
            "select l:name, count(t:name), e:name\n" +
                    "where l:name='JournalReview'\n" +
                    "group by t:name, ^e:name\n" +
                    "order by count(t:name) desc\n" +
                    "limit l:1\n"
        )
        assertEquals(2298L, stream.readVersion())
        assertEquals(1, stream.count())

        val log = stream.first()
        assertEquals("JournalReview", log.conceptName)
        assertNull(log.lifecycleModel)
        assertNull(log.identityId)
        assertEquals("JournalReview", log.attributes[CONCEPT_NAME] as String)
        standardAndAllAttributesMatch(log, log)

        assertEquals(101, log.traces.count())

        for (trace in log.traces) {
            assertEquals(trace.attributes["count(trace:concept:name)"] as Long, trace.count.toLong())
            assertEquals(1, trace.count)
        }
    }

    /**
     * Demonstrates the bug of mixing events from different logs when grouping traces into variants.
     */
    @Test
    fun groupByWithTwoLogs() {
        val stream = q(
            "select l:name, count(t:name), e:name\n" +
                    "where l:id in ($journal, $bpi)" +
                    "group by ^e:name\n" +
                    "order by count(t:name) desc"
        )
        // bpi has no version information, hence only info for journal is taken
        assertEquals(2298L, stream.readVersion())

        assertEquals(2, stream.count())
        val logs = stream.toList()
        for (trace in logs[0].traces) {
            for (event in trace.events)
                assertTrue(event.conceptName in eventNames)
        }

        for (trace in logs[1].traces) {
            for (event in trace.events)
                assertTrue(event.conceptName in bpiEventNames)
        }
    }

    /**
     * Demonstrates the bug #107: the lack of grouping on the lower scope in a multi-scoped group by query.
     */
    @Test
    fun multiScopeGroupBy() {
        val stream = q(
            "select l:name, t:name, max(^e:timestamp) - min(^e:timestamp), e:name, count(e:name)\n" +
                    "where l:id=$journal\n" +
                    "group by t:name, e:name\n"
        )

        assertEquals(2298L, stream.readVersion())
        assertEquals(1, stream.count())
        val log = stream.first()

        assertEquals(101, log.traces.count())

        for (trace in log.traces) {
            val grouped = trace.events.groupBy { it.conceptName }
            for ((name, group) in grouped) {
                assertEquals(1, group.size)
                assertEquals(name, group.first().conceptName)
            }
        }
    }

    /**
     * Demonstrates the bug #116 - missing attributes when selecting many expressions
     * The actual bug was in the presentation layer in the JSON parser.
     */
    @Test
    fun missingAttributes() {
        val stream = q(
            "select l:name, t:name, min(^e:timestamp), max(^e:timestamp), max(^e:timestamp)-min(^e:timestamp) " +
                    "where l:id=$hospital " +
                    "group by t:name " +
                    "limit l:1, t:10"
        )

        assertEquals(1, stream.count())
        val log = stream.first()

        assertEquals(10, log.traces.count())

        for (trace in log.traces) {
            assertNotNull(trace.conceptName)
            assertNotNull(trace.attributes[CONCEPT_NAME])
            assertEquals(trace.conceptName, trace.attributes[CONCEPT_NAME])
            assertNotNull(trace.attributes["min(^event:time:timestamp)"])
            assertNotNull(trace.attributes["max(^event:time:timestamp)"])
            assertNotNull(trace.attributes["max(^event:time:timestamp) - min(^event:time:timestamp)"])
        }
    }

    /**
     * Demonstrates the bug #116:
     * PSQLException: ERROR: operator does not exist: timestamp with time zone[] - timestamp with time zone[]
     */
    @Test
    fun orderByAggregationExpression() {
        val stream = q(
            "select max(^e:timestamp)-min(^e:timestamp)" +
                    "where l:id=$hospital " +
                    "group by t:name " +
                    "order by max(^e:timestamp)-min(^e:timestamp) desc"
        )

        assertEquals(1, stream.count())
        val log = stream.first()

        assertTrue(log.traces.count() > 10)

        var lastDuration: Double = Double.MAX_VALUE
        for (trace in log.traces) {
            val duration = trace.attributes["max(^event:time:timestamp) - min(^event:time:timestamp)"] as Double
            assertTrue(duration <= lastDuration)
            lastDuration = duration
        }
    }

    /**
     * Demonstrates the bug where multi-level implicit group by with hoisting causes counting of duplicates.
     */
    @Test
    fun multiScopeImplicitGroupBy() {
        val stream = q("select count(l:name), count(^t:name), count(^^e:name) where l:id=$journal").first()
        assertEquals(1L, stream.attributes["count(log:concept:name)"])
        assertEquals(101L, stream.attributes["count(^trace:concept:name)"])
        assertEquals(2298L, stream.attributes["count(^^event:concept:name)"])
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
        val stream = q("where l:id=$journal offset l:1")
        assertEquals(0, stream.count())
        assertEquals(0L, stream.readVersion())

        val journalAll = q("where l:name like 'Journal%'")
        val journalWithOffset = q("where l:name like 'Journal%' offset l:1")
        assertEquals(max(journalAll.count() - 1, 0), journalWithOffset.count())
    }

    @Test
    fun offsetAllTest() {
        val stream = q("where l:id=$journal offset e:3, t:2, l:1")
        assertEquals(0, stream.count())
        assertEquals(0L, stream.readVersion())

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

    /**
     * #117 - tests whether the nested attributes are read
     */
    @Test
    fun readNestedAttributes() {
        val stream = q("where l:id=$hospital limit l:1, t:1, e:1")
        assertEquals(1, stream.count())

        val log = stream.first()
        val attribute = log.attributes["meta_concept:named_events_total"]!!

        assertEquals(150291L, attribute)
        with(log.attributes.children("meta_concept:named_events_total")) {
            assertEquals(624, this.size)

            assertEquals(23L, this["haptoglobine"])
            assertEquals(24L, this["ijzer"])
            assertEquals(27L, this["bekken"])
            assertEquals(2L, this["cortisol"])
            assertEquals(9L, this["ammoniak"])
        }
    }

    /**
     * #117 - tests whether the nested attributes are skipped
     */
    @Test
    fun skipNestedAttributes() {
        val stream = DBHierarchicalXESInputStream(dbName, Query("where l:id=$hospital limit l:1, t:1, e:1"), false)
        assertEquals(1, stream.count())

        val log = stream.first()
        val attribute = log.attributes["meta_concept:named_events_total"]!!

        assertEquals(150291L, attribute)
        with(log.attributes.children("meta_concept:named_events_total")) {
            assertTrue(this.isEmpty())

            assertFalse("haptoglobine" in this)
            assertFalse("ijzer" in this)
            assertFalse("bekken" in this)
            assertFalse("cortisol" in this)
            assertFalse("ammoniak" in this)
        }
    }

    @Test
    fun `where on a nested attribute`() {
        val stream = DBHierarchicalXESInputStream(
            dbName,
            Query("where (l:id=$hospital or l:id=$journal) and [l:${SEPARATOR}${STRING_MARKER}meta_org:group_events_average${SEPARATOR}Maternity ward]='0.016' limit l:1, t:1, e:1"),
            true
        )
        assertTrue { stream.readVersion() >= 1L }
        val log = stream.first()
        assertEquals(1.728, log.attributes.children("meta_org:group_events_average")["Pathology"])
    }
}
