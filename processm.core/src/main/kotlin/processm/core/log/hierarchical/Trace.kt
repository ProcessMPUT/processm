package processm.core.log.hierarchical

import processm.core.log.Event
import processm.core.log.XESComponent
import processm.core.log.XESInputStream
import processm.core.log.attribute.Attribute
import processm.core.log.Trace as BaseTrace

/**
 * An extension of [processm.core.log.Trace] that supports direct access to underlying events.
 */
class Trace(
    events: Sequence<Event> = emptySequence(),
    attributesInternal: MutableMap<String, Attribute<*>> = HashMap()
) : BaseTrace(attributesInternal) {
    /**
     * A lazy sequence of events in this trace.
     */
    var events: Sequence<Event> = events
        internal set
}

/**
 * Transforms this trace into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESComponent
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Trace.toFlatSequence(): XESInputStream = sequenceOf(this).toFlatSequence()

/**
 * Transforms this sequence of traces into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESComponent
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Sequence<Trace>.toFlatSequence(): XESInputStream = object : XESInputStream {
    override fun iterator(): Iterator<XESComponent> = sequence {
        this@toFlatSequence.forEach {
            yield(it)
            yieldAll(it.events)
        }
    }.iterator()
}
