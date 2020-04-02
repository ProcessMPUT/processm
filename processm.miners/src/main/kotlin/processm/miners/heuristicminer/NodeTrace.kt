package processm.miners.heuristicminer

import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Node

internal typealias NodeTrace = List<Node>


internal fun node(e: Event): Node {
    //TODO: do it right once appropriate interface is in place
    return Node(e.conceptName.toString(), e.conceptInstance ?: "")
}

interface TraceToNodeTrace {
    operator fun invoke(trace: Trace): NodeTrace
}

class BasicTraceToNodeTrace : TraceToNodeTrace {
    override fun invoke(trace: Trace): NodeTrace =
        trace.events
            .filter { it.lifecycleTransition == "complete" || it.lifecycleTransition == null }
            .map { node(it) }
            .toList()
}