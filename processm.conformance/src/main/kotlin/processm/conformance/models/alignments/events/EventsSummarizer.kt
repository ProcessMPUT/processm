package processm.conformance.models.alignments.events

import processm.core.log.Event
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.StringAttr
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity

/**
 * Transforms a [Trace] or a list of [Event] into some summary such that if two traces have the same summary they are indistinguishable in further processing.
 *
 * For example, maybe only event names are relevant for further processing and all other attributes are ignored - a summary may be a list of names
 */
fun interface EventsSummarizer<out T> {
    operator fun invoke(events: Iterable<Event>): T
    operator fun invoke(trace: Trace): T = invoke(trace.events.asIterable())

    fun summary(activities: Iterable<Activity>): T = invoke(
        activities.map { Event(mutableMapOf(Attribute.CONCEPT_NAME to StringAttr(Attribute.CONCEPT_NAME, it.name))) }
    )
}

/**
 * Returns a sequence of the same length as [log], such that its i-th element is equal to the result of calling `block`
 * on the i-th element of [log], but computed more efficiently: for each [Trace] summary, [block] is called exactly once.
 * The evaluation is lazy due to use of [Sequence].
 */
inline fun <reified R> EventsSummarizer<*>.flatMap(log: Sequence<Trace>, crossinline block: (Trace) -> R): Sequence<R> {
    val alignments = HashMap<Any?, Any>()
    return log.map { trace ->
        alignments.computeIfAbsent(this@flatMap(trace)) { block(trace) ?: Unit } as? R as R
    }
}

/**
 * Returns a list of the same length as [log], such that `log.map(block) == flatMap(log, block)`, but computed more
 * efficiently: for each [Trace] summary (a result of calling [invoke]), [block] is called exactly once.
 */
inline fun <reified R> EventsSummarizer<*>.flatMap(log: Iterable<Trace>, crossinline block: (Trace) -> R): List<R> {
    val alignments = HashMap<Any?, Any>()
    return log.map { trace ->
        alignments.computeIfAbsent(this@flatMap(trace)) { block(trace) ?: Unit } as? R as R
    }
}

/**
 * Returns a sequence of the same length as [log], such that its i-th element is equal to the result of calling `block`
 * on the i-th trace of [log], but computed more efficiently: for each [Trace] summary, [block] is called exactly once.
 * The evaluation is lazy due to use of [Sequence].
 */
inline fun <reified R> EventsSummarizer<*>.flatMap(log: Log, crossinline block: (Trace) -> R) =
    flatMap(log.traces, block)
