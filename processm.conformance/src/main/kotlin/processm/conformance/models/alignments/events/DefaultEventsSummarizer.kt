package processm.conformance.models.alignments.events

import processm.core.log.Event

class DefaultEventsSummarizer : EventsSummarizer<List<Event>> {
    override fun invoke(events: List<Event>) = events
}