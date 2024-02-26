package processm.core.log

import processm.core.DBTestHelper
import processm.core.log.attribute.Attribute.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.metadata.BasicMetadata.LEAD_TIME
import processm.core.models.metadata.BasicMetadata.SERVICE_TIME
import processm.core.models.metadata.BasicMetadata.SUSPENSION_TIME
import processm.core.models.metadata.BasicMetadata.WAITING_TIME
import processm.core.querylanguage.Query
import processm.helpers.fastParseISO8601
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InMemoryXESProcessing::class)
class InferTimesTests {

    private val journal by lazy {
        HoneyBadgerHierarchicalXESInputStream(
            InferTimes(
                DBHierarchicalXESInputStream(
                    DBTestHelper.dbName,
                    Query("where l:id=${DBTestHelper.JournalReviewExtra}")
                ).toFlatSequence()
            )
        ).first()
    }

    private val dummyBPAF by lazy {
        HoneyBadgerHierarchicalXESInputStream(
            InferTimes(
                sequenceOf(
                    Log(mutableAttributeMapOf(LIFECYCLE_MODEL to "bpaf")),
                    Trace(),
                    Event(
                        mutableAttributeMapOf(
                            CONCEPT_NAME to "A",
                            CONCEPT_INSTANCE to "1",
                            LIFECYCLE_STATE to "Open.Running.InProgress",
                            TIME_TIMESTAMP to "2022-07-05T16:27:00.00Z".fastParseISO8601()
                        )
                    ),
                    Event(
                        mutableAttributeMapOf(
                            CONCEPT_NAME to "A",
                            CONCEPT_INSTANCE to "1",
                            LIFECYCLE_STATE to "Closed.Completed",
                            TIME_TIMESTAMP to "2022-07-05T16:30:00.00Z".fastParseISO8601()
                        )
                    )
                )
            )
        ).first()
    }

    @Test
    fun `lead time equals the time between the first and the last event`() {
        for (trace in journal.traces) {
            val first = trace.events.first()
            val last = trace.events.last()
            val expected = Duration.between(first.timeTimestamp, last.timeTimestamp)
            val actual = Duration.parse(trace[LEAD_TIME.urn] as CharSequence)
            assertEquals(expected, actual)

            for (event in trace.events) {
                assertTrue(Duration.parse(event[LEAD_TIME.urn] as CharSequence).compareTo(expected) <= 0)
            }
        }
    }

    @Test
    fun `trace service waiting and suspension times equal the sum of events service waiting and suspension times`() {
        for (trace in journal.traces) {
            val expService = trace.events.map { Duration.parse(it[SERVICE_TIME.urn] as String) }.sumOf { it.toMillis() }
            val expWaiting = trace.events.map { Duration.parse(it[WAITING_TIME.urn] as String) }.sumOf { it.toMillis() }
            val expSuspension =
                trace.events.map { Duration.parse(it[SUSPENSION_TIME.urn] as String) }.sumOf { it.toMillis() }
            val actService = Duration.parse(trace[SERVICE_TIME.urn] as CharSequence)
            val actWaiting = Duration.parse(trace[WAITING_TIME.urn] as CharSequence)
            val actSuspension = Duration.parse(trace[SUSPENSION_TIME.urn] as CharSequence)
            assertEquals(expService, actService.toMillis())
            assertEquals(expWaiting, actWaiting.toMillis())
            assertEquals(expSuspension, actSuspension.toMillis())
            assertEquals(0L, actSuspension.toMillis())

            for (event in trace.events) {
                assertTrue(Duration.parse(event[SERVICE_TIME.urn] as CharSequence).compareTo(actService) <= 0)
                assertTrue(Duration.parse(event[WAITING_TIME.urn] as CharSequence).compareTo(actWaiting) <= 0)
                assertTrue(Duration.parse(event[SUSPENSION_TIME.urn] as CharSequence).compareTo(actSuspension) <= 0)
            }
        }
    }

    @Test
    fun `lead and service time of dummy BPAF is 3 min`() {
        val trace = dummyBPAF.traces.first()
        assertEquals("PT3M", trace[LEAD_TIME.urn])
        assertEquals("PT3M", trace[SERVICE_TIME.urn])
        assertEquals("PT0S", trace[WAITING_TIME.urn])
        assertEquals("PT0S", trace[SUSPENSION_TIME.urn])

        val last = trace.events.last()
        assertEquals("PT3M", last[LEAD_TIME.urn])
        assertEquals("PT3M", last[SERVICE_TIME.urn])
        assertEquals("PT0S", last[WAITING_TIME.urn])
        assertEquals("PT0S", last[SUSPENSION_TIME.urn])
    }
}
