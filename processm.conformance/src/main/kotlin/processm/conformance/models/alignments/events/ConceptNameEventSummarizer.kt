package processm.conformance.models.alignments.events

import processm.core.log.Event
import processm.core.models.commons.Activity

/**
 * An event summarizer that distinguishes events using their `concept:name` attributes.
 */
object ConceptNameEventSummarizer : EventsSummarizer<List<String?>> {
    override fun invoke(events: Iterable<Event>): List<String?> = events.map(Event::conceptName)
    override fun summary(activities: Iterable<Activity>): List<String?> = activities.map(Activity::name)
}
