package processm.conformance.models.alignments.events

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

/**
 * Transforms a [Trace] or a list of [Event] into some summary such that if two traces have the same summary they are indistinguishable in further processing.
 *
 * For example, maybe only event names are relevant for further processing and all other attributes are ignored - a summary may be a list of names
 */
fun interface EventsSummarizer<T> {
    operator fun invoke(events: List<Event>): T
    operator fun invoke(trace: Trace): T = invoke(trace.events.toList())

    /**
     * Returns a list of the same length as [log], such that `log.map(block) == flatMap(log, block)`, but computed more
     * efficiently: for each [Trace] summary (a result of calling [invoke]), [block] is called exactly once.
     */
    fun <R> flatMap(log: Iterable<Trace>, block: (Trace) -> R): List<R> {
        // Using Pairs for cases when R is a nullable type to avoid calling block multiple times
        val alignments = HashMap<Any?, Pair<Boolean, R>>()
        return log.map { trace ->
            alignments
                .computeIfAbsent(this@EventsSummarizer(trace)) { true to block(trace) }
                .second
        }
    }

    /**
     * Returns a sequence of the same length as [log], such that its i-th element is equal to the result of calling `block`
     * on the i-th element of [log], but computed more efficiently: for each [Trace] summary, [block] is called exactly once.
     * The evaluation is lazy due to use of [Sequence].
     */
    fun <R> flatMap(log: Sequence<Trace>, block: (Trace) -> R): Sequence<R> {
        val alignments = HashMap<Any?, Pair<Boolean, R>>()
        return log.map { trace ->
            alignments
                .computeIfAbsent(this@EventsSummarizer(trace)) { true to block(trace) }
                .second
        }
    }

    /**
     * Returns a sequence of the same length as [log], such that its i-th element is equal to the result of calling `block`
     * on the i-th trace of [log], but computed more efficiently: for each [Trace] summary, [block] is called exactly once.
     * The evaluation is lazy due to use of [Sequence].
     */
    fun <R> flatMap(log: Log, block: (Trace) -> R) = flatMap(log.traces, block)

}