package processm.conformance.models.alignments.events

import processm.core.log.Event

/**
 * An event summarizer that distinguishes events using their `concept:name` attributes.
 */
object ConceptNameEventSummarizer : EventsSummarizer<List<String?>> {
    override fun invoke(events: Iterable<Event>): List<String?> = events.map(Event::conceptName)
}
