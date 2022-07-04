package processm.core.log

import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.metadata.BasicMetadata.LEAD_TIME
import processm.core.models.metadata.BasicMetadata.SERVICE_TIME
import processm.core.models.metadata.BasicMetadata.SUSPENSION_TIME
import processm.core.models.metadata.BasicMetadata.WAITING_TIME
import processm.core.querylanguage.Query
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
}
