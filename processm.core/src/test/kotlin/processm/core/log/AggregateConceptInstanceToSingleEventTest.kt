package processm.core.log

import processm.core.log.Helpers.event
import processm.core.log.attribute.Attribute.Companion.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.Companion.LIFECYCLE_TRANSITION
import processm.core.log.hierarchical.toFlatSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import processm.core.log.hierarchical.Log as HierarchicalLog
import processm.core.log.hierarchical.Trace as HierarchicalTrace

class AggregateConceptInstanceToSingleEventTest {

    @Test
    fun sequence() {
        val trace = listOf(
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
        )
        val log = HierarchicalLog(sequenceOf(HierarchicalTrace(trace.asSequence())))
        val stream = AggregateConceptInstanceToSingleEvent(sequenceOf(log).toFlatSequence()).iterator()
        assertIs<Log>(stream.next())
        assertIs<Trace>(stream.next())
        assertEquals(trace[1], stream.next())
        assertEquals(trace[3], stream.next())
        assertEquals(trace[5], stream.next())
        assertEquals(trace[7], stream.next())
        assertFalse(stream.hasNext())
    }

    @Test
    fun `interleaving - last`() {
        val trace = listOf(
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
        )
        val log = HierarchicalLog(sequenceOf(HierarchicalTrace(trace.asSequence())))
        val stream = AggregateConceptInstanceToSingleEvent(sequenceOf(log).toFlatSequence()).iterator()
        assertIs<Log>(stream.next())
        assertIs<Trace>(stream.next())
        assertEquals(trace[4], stream.next())
        assertEquals(trace[5], stream.next())
        assertEquals(trace[6], stream.next())
        assertEquals(trace[7], stream.next())
        assertFalse(stream.hasNext())
    }

    @Test
    fun `interleaving - first`() {
        val trace = listOf(
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "start"),
            event("a", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 1, LIFECYCLE_TRANSITION to "end"),
            event("a", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
            event("b", CONCEPT_INSTANCE to 2, LIFECYCLE_TRANSITION to "end"),
        )
        val log = HierarchicalLog(sequenceOf(HierarchicalTrace(trace.asSequence())))
        val stream = AggregateConceptInstanceToSingleEvent(
            sequenceOf(log).toFlatSequence(),
            aggregator = List<Pair<Int, Event>>::first
        ).iterator()
        assertIs<Log>(stream.next())
        assertIs<Trace>(stream.next())
        assertEquals(trace[0], stream.next())
        assertEquals(trace[1], stream.next())
        assertEquals(trace[2], stream.next())
        assertEquals(trace[3], stream.next())
        assertFalse(stream.hasNext())
    }

    @Test
    fun `two identical traces without instances`() {
        val trace = listOf(event("a"), event("b"))
        val log =
            HierarchicalLog(sequenceOf(HierarchicalTrace(trace.asSequence()), HierarchicalTrace(trace.asSequence())))
        val base = sequenceOf(log).toFlatSequence()
        val stream = AggregateConceptInstanceToSingleEvent(base)
        assertEquals(base.toList(), stream.toList())
    }

    @Test
    fun `two identical logs with a two traces without instances`() {
        val trace = listOf(event("a"), event("b"))
        val log =
            HierarchicalLog(sequenceOf(HierarchicalTrace(trace.asSequence()), HierarchicalTrace(trace.asSequence())))
        val base = sequenceOf(log, log).toFlatSequence()
        val stream = AggregateConceptInstanceToSingleEvent(base)
        assertEquals(base.toList(), stream.toList())
    }
}