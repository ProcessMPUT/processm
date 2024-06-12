package processm.conformance.models.alignments

import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.events.flatMap
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
     * @param trace The trace to align.
     * @throws IllegalStateException If the alignment cannot be calculated, e.g., because the final model state is not
     * reachable.
     */
    fun align(trace: Trace): Alignment =
        align(trace, Int.MAX_VALUE)
            ?: throw IllegalStateException("Cannot align the log with the model. The final state of the model is not reachable.")

    /**
     * Calculates [Alignment] for the given [trace].
     *
     * @param trace The trace to align.
     * @param costUpperBound The maximal cost of the resulting alignment.
     * @return The alignment with the minimal cost or null if an alignment with the cost within the [costUpperBound] does
     * not exist.
     */
    fun align(trace: Trace, costUpperBound: Int): Alignment?

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(log: Sequence<Trace>, summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer): Sequence<Alignment> =
        summarizer?.flatMap(log) { trace -> align(trace) } ?: log.map { align(it) }

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param costUpperBound The maximal cost of the resulting alignment.
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(
        log: Sequence<Trace>,
        costUpperBound: Int,
        summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer
    ): Sequence<Alignment?> =
        summarizer?.flatMap(log) { trace -> align(trace, costUpperBound) } ?: log.map { align(it, costUpperBound) }

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(log: Log, summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer): Sequence<Alignment> =
        align(log.traces, summarizer)

    /**
     * Calculates [Alignment]s for the given [log].
     *
     * @param costUpperBound The maximal cost of the resulting alignment.
     * @param summarizer An event summarizer to use, traces with equal summaries are grouped together and the alignment
     * for them is computed only once. If `null`, no grouping is performed and alignment is computed for each trace separately,
     * event if there are repetitions in [log].
     * @see align
     */
    fun align(
        log: Log,
        costUpperBound: Int = Int.MAX_VALUE,
        summarizer: EventsSummarizer<*>? = DefaultEventsSummarizer
    ): Sequence<Alignment?> =
        align(log.traces, costUpperBound, summarizer)

    /**
     * Resets the internal state of the aligner that may consist of computed information about the model. Call this
     * method whenever the [model] is mutated.
     *
     * Implementation of this function is optional.
     */
    @ResettableAligner
    fun reset() {
    }
}

@RequiresOptIn(message = "Implementation of this function is optional and so its use requires a special care as the aligner may simply ignore the reset request.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ResettableAligner
