package processm.conformance.models.alignments.events

import processm.core.helpers.asList
import processm.core.log.Event
import processm.core.log.hierarchical.Trace

/**
 * An [EventsSummarizer] assuming that every piece of information is important
 */
object DefaultEventsSummarizer : EventsSummarizer<List<Event>> {
    // Note that the general contract for List<T> requires that hashCode() and equals() use list content.
    override fun invoke(events: Iterable<Event>): List<Event> = events.asList()

    override fun invoke(trace: Trace): List<Event> = trace.events.asList()
}
