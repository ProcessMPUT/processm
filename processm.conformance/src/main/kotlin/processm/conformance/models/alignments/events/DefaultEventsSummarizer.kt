package processm.conformance.models.alignments.events

import processm.core.log.Event

/**
 * An [EventsSummarizer] assuming that every piece of information is important
 */
class DefaultEventsSummarizer : EventsSummarizer<List<Event>> {
    override fun invoke(events: List<Event>) = events
}