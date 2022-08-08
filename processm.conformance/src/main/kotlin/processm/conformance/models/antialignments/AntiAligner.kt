package processm.conformance.models.antialignments

import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.events.ConceptNameEventSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel

interface AntiAligner {
    /**
     * The penalty function used by [align]
     */
    val penalty: PenaltyFunction

    /**
     * The model used by [align]
     */
    val model: ProcessModel

    /**
     * Calculates an anti-alignment using the given [log] and the maximal [size] of the anti-alignment. The size of
     * the anti-alignment here is defined as the total number of labeled activities in the anti-alignment.
     * @param eventsSummarizer determines the equality conditions for events and traces.
     */
    fun align(
        log: Sequence<Trace>,
        size: Int,
        eventsSummarizer: EventsSummarizer<*> = ConceptNameEventSummarizer
    ): Collection<AntiAlignment>

    /**
     * Calculates an anti-alignment using the given [log] and the maximal [size] of the anti-alignment. The size of
     * the anti-alignment here is defined as the total number of labeled activities in the anti-alignment.
     * @param eventsSummarizer determines the equality conditions for events and traces.
     */
    fun align(log: Log, size: Int, eventsSummarizer: EventsSummarizer<*> = ConceptNameEventSummarizer) =
        align(log.traces, size, eventsSummarizer)
}
