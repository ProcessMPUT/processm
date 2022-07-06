package processm.core.log.hierarchical

import processm.core.helpers.mapToSet
import processm.core.helpers.parseISO8601
import processm.core.helpers.toDateTime
import processm.core.log.attribute.Attribute.Companion.COST_TOTAL
import processm.core.log.attribute.StringAttr
import processm.core.log.attribute.value
import java.time.DayOfWeek
import java.util.*
import kotlin.test.*

class DBHierarchicalXESInputStreamWithWhereQueryTests : DBHierarchicalXESInputStreamWithQueryTestsBase() {
    @Test
    fun whereSimpleTest() {
        val stream = q("where dayofweek(e:timestamp) in (1, 7) and l:id=$journal")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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
    fun whereSimpleWithHoistingTest() {
        val stream = q("where dayofweek(^e:timestamp) in (1, 7) and l:id=$journal")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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
    fun whereSimpleWithHoistingTest2() {
        val stream = q("where dayofweek(^^e:timestamp) in (1, 7) and l:id=$journal")
        val validDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        assertEquals(1, stream.count())

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
    fun whereLogicExprWithHoistingTest() {
        val stream = q("where not(t:currency = ^e:currency) and l:id=$journal")
        assertEquals(1, stream.count())

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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExprTest() {
        val stream = q("where t:currency != e:currency and l:id=$journal")
        assertEquals(1, stream.count())

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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExpr2Test() {
        val stream = q("where not(t:currency = ^e:currency) and t:total is null and l:id=$journal")
        assertEquals(1, stream.count())

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
                assertNotNull(event.conceptInstance?.toIntOrNull())
                assertEquals("USD", event.costCurrency)
                assertEquals(1.08, event.costTotal)
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
    fun whereLogicExpr3Test() {
        val stream =
            q("where (not(t:currency = ^e:currency) or ^e:timestamp >= D2007-01-01) and t:total is null and l:id=$journal")
        val myBegin = "2007-01-01T00:00:00Z".parseISO8601()
        assertEquals(1, stream.count())

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
            q("where t:name like '%5' and ^e:resource matches '^[SP]am$' and l:id=$journal")
        val nameRegex = Regex("^[SP]am$")
        assertEquals(1, stream.count())

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

        assertTrue(log.traces.count() > 0)

        for (trace in log.traces) {
            assertTrue(trace.conceptName!!.endsWith("5"))
            assertTrue(trace.events.any { e -> nameRegex.matches(e.orgResource!!) })
            assertTrue(trace.events.any { e -> !nameRegex.matches(e.orgResource!!) })
        }
    }


    /**
     * Demonstrates the bug #116 - seeking for the traces with non-null non-standard attribute causes exception:
     * PSQLException: ERROR: invalid input value for enum attribute_type: "uuid"
     * Where: PL/pgSQL function get_trace_attribute(bigint,text,attribute_type,anynonarray) line 31 at IF
     */
    @Test
    fun whereNotNull() {
        val stream = q("where l:id=$journal and [t:cost:total] is not null")

        assertEquals(1, stream.count())
        val log = stream.first()

        assertNotEquals(101, log.traces.count())

        for (trace in log.traces) {
            assertNotNull(trace.costTotal)
            assertNotNull(trace.attributes[COST_TOTAL]?.value)
        }
    }

    /**
     * Demonstrates the bug #116 - seeking for the traces with non-null non-standard attribute retrieves
     * traces with null attribute
     * The actual bug was in the presentation layer in the JSON parser.
     */
    @Test
    fun whereNotNull2() {
        val stream = q("where l:id=$hospital and [t:Diagnosis] is not null")

        assertEquals(1, stream.count())
        val log = stream.first()


        for (trace in log.traces) {
            assertNotNull(trace.attributes["Diagnosis"]?.value)
            assertFalse(trace.attributes["Diagnosis"]?.valueToString().isNullOrBlank())
        }
    }
}
