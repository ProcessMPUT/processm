package processm.experimental.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * A trace register storing for each binding a single shortest trace seen so far, preferring older (i.e., a trace must be shorter in order to replace existing)
 */
class SingleShortestTraceRegister : AbstractTraceRegister() {

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings) {
            if (bindingCounter[binding].any { it.size < nodeTrace.size })
                continue
            bindingCounter[binding].clear()
            bindingCounter[binding].add(nodeTrace)
        }
    }
}