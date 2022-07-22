package processm.conformance.models.alignments.events

import processm.core.log.Event

/**
 * An [EventsSummarizer] assuming that every piece of information is important
 */
object DefaultEventsSummarizer : EventsSummarizer<Iterable<Event>> {
    override fun invoke(events: Iterable<Event>) = events
}
