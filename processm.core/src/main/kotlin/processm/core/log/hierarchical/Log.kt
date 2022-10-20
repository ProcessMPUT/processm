package processm.core.log.hierarchical

import processm.core.log.AttributeMap
import processm.core.log.MutableAttributeMap
import processm.core.log.XESComponent
import processm.core.log.XESInputStream
import processm.core.log.attribute.Attribute
import processm.core.log.Log as BaseLog

/**
 * An extension of [processm.core.log.Log] that supports direct access to underlying traces.
 */
class Log(
    traces: Sequence<Trace> = emptySequence(),
    attributesInternal: MutableAttributeMap = MutableAttributeMap()
) : BaseLog(attributesInternal) {
    /**
     * A lazy sequence of trace in this log.
     */
    var traces: Sequence<Trace> = traces
        internal set
}

/**
 * Transforms this log into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESComponent
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Log.toFlatSequence(): XESInputStream = sequenceOf(this).toFlatSequence()

/**
 * Transforms this sequence of logs into a flat sequence of XES elements.
 * @see XESInputStream
 * @see XESComponent
 * @see processm.core.log.Log
 * @see processm.core.log.Trace
 * @see processm.core.log.Event
 */
fun Sequence<Log>.toFlatSequence(): XESInputStream = sequence {
    this@toFlatSequence.forEach {
        yield(it)
        yieldAll(it.traces.toFlatSequence())
    }
}
