package processm.core.log.hierarchical

import processm.core.log.XESComponent
import processm.core.log.XESInputStream

/**
 * A stream working in the opposite way than [HoneyBadgerHierarchicalXESInputStream]:
 * from a hierarchical [LogInputStream] to a flat [XESInputStream]. Useful when you have a [LogInputStream], e.g.,
 * from [HoneyBadgerHierarchicalXESInputStream] or one of the methods of [processm.core.log.Helpers].
 *
 * There should be no need for such a transformation in the production code, so the class is on purpose in tests.
 */
class FlatteningXESInputStream(val base: LogInputStream) : XESInputStream {
    override fun iterator(): Iterator<XESComponent> = iterator {
        for (log in base) {
            yield(log)
            for (trace in log.traces) {
                yield(trace)
                yieldAll(trace.events)
            }
        }
    }
}