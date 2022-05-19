package processm.conformance.rca

import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.Step
import processm.core.log.Event

/**
 * Searches the given [Alignment] for [Step]s concerning the activity named [activityName] and events with the [Event.conceptName] equal to [eventName]
 * If every time they coincide, returns [MatchResult.NO_MATCH].
 * If either there is no [Step] with the activity [activityName] or with the event [eventName], returns [MatchResult.IRRELEVANT]
 * Otherwise, returns [MatchResult.MATCH]
 *
 * The idea is that a matching alignment is one where the given event was executed at a different time than it should have been according to the model.
 * Conversely, a non-matching alignment is one where the event and the activity were always executed simultaneously.
 * Finally, an irrelevant alignment is one without either any execution of such an event or the activity.
 */
class ActivityEventCriterion(val activityName: String, val eventName: String) : Criterion {
    override fun invoke(alignment: Alignment): MatchResult {
        var hasActivity = false
        var hasEvent = false
        var hasUnmatchedEvent = false
        var hasUnmatchedActivity = false
        for (step in alignment.steps) {
            if (step.logMove?.conceptName == eventName) {
                hasEvent = true
                if (step.modelMove?.name !== activityName) {
                    if (hasUnmatchedActivity)
                        return MatchResult.MATCH
                    hasUnmatchedEvent = true
                }
            }
            if (step.modelMove?.name == activityName) {
                hasActivity = true
                if (step.logMove?.conceptName !== eventName) {
                    if (hasUnmatchedEvent)
                        return MatchResult.MATCH
                    hasUnmatchedActivity = true
                }
            }
        }
        return if (hasActivity && hasEvent) MatchResult.NO_MATCH else MatchResult.IRRELEVANT
    }
}