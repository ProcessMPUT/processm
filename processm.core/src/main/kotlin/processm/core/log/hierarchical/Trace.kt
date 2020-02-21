package processm.core.log.hierarchical

import processm.core.log.Event
import processm.core.log.XESElement
import processm.core.log.XESInputStream
import processm.core.log.Trace as BaseTrace

/**
 * An extension of [processm.core.log.Trace] that supports direct access to underlying events.
 * @property events A lazy sequence of events in this trace.
 */
class Trace(val events: Sequence<Event>) : BaseTrace() {
}

/**
 * Transforms this trace into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESElement
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Trace.toFlatSequence(): XESInputStream = sequenceOf(this).toFlatSequence()

/**
 * Transforms this sequence of traces into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESElement
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Sequence<Trace>.toFlatSequence(): XESInputStream = object : XESInputStream {
    override fun iterator(): Iterator<XESElement> = sequence<XESElement> {
        this@toFlatSequence.forEach {
            yield(it)
            yieldAll(it.events)
        }
    }.iterator()
}