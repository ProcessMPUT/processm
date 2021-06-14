package processm.experimental.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.miners.onlineminer.NodeTrace

/**
 * Stores each and every trace
 */
class CompleteTraceRegister : AbstractTraceRegister() {

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings)
            bindingCounter[binding].add(nodeTrace)
    }
}