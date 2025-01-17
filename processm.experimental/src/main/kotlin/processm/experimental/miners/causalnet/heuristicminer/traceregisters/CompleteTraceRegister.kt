package processm.experimental.miners.causalnet.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * Stores each and every trace
 */
class CompleteTraceRegister : AbstractTraceRegister() {

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings)
            bindingCounter[binding].add(nodeTrace)
    }
}