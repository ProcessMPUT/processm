package processm.conformance.models.alignments

import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel

/**
 * An interface for generic model and trace aligner.
 */
interface Aligner {

    /**
     * The penalty function used by [align]
     */
    val penalty: PenaltyFunction

    /**
     * The model used by [align]
     */
    val model: ProcessModel

    /**
     * Calculates [Alignment] for the given [trace].
     *
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     */
    fun align(trace: Trace): Alignment

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(log: Sequence<Trace>, summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer()) =
        summarizer?.flatMap(log) { trace -> align(trace) } ?: log.map { align(it) }

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(log: Log, summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer()) = align(log.traces, summarizer)
}