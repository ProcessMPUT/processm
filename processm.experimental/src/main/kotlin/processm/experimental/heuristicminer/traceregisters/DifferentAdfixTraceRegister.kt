package processm.experimental.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Split
import processm.experimental.heuristicminer.NodeTrace
import kotlin.math.max
import kotlin.math.min

/**
 * A trace register storing traces with different prefixes (for joins) and suffixes (for splits), while ignoring cardinality and order in the prefix/suffix.
 * The rationale is that it is only important what activities can possibly be executed before/after, not any particular order of execution.
 */
class DifferentAdfixTraceRegister : AbstractTraceRegister() {

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings) {
            if (binding is Split) {
                val idx = nodeTrace.lastIndexOf(binding.source)
                check(idx >= 0)
                val suffix = nodeTrace.subList(idx, nodeTrace.size).toSet()
                if (bindingCounter[binding].any {
                        it.subList(max(it.size - suffix.size, 0), it.size).toSet() == suffix
                    })
                    continue
            } else {
                require(binding is Join)
                val idx = nodeTrace.indexOf(binding.target)
                check(idx >= 0)
                val prefix = nodeTrace.subList(0, idx).toSet()
                if (bindingCounter[binding].any { it.subList(0, min(idx, it.size)).toSet() == prefix })
                    continue
            }
            bindingCounter[binding].add(nodeTrace)
        }
    }
}