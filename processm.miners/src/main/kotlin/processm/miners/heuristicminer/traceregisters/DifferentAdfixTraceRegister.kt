package processm.miners.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Split
import kotlin.math.max
import kotlin.math.min

/**
 * A trace register storing traces with different prefixes (for joins) and suffixes (for splits)
 */
class DifferentAdfixTraceRegister : AbstractTraceRegister() {

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings) {
            require(binding is Split || binding is Join)
            if (binding is Split) {
                val idx = nodeTrace.lastIndexOf(binding.source)
                require(idx >= 0)
                val suffix = nodeTrace.subList(idx, nodeTrace.size)
                if (bindingCounter[binding].any { it.subList(max(it.size - suffix.size, 0), it.size) == suffix })
                    continue
            } else {
                val idx = nodeTrace.indexOf((binding as Join).target)
                require(idx >= 0)
                val prefix = nodeTrace.subList(0, idx)
                if (bindingCounter[binding].any { it.subList(0, min(idx, it.size)) == prefix })
                    continue
            }
            bindingCounter[binding].add(nodeTrace)
        }
    }
}