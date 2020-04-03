package processm.miners.heuristicminer

import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Node

internal typealias NodeTrace = List<Node>

/**
 * Convert a given [Trace] to a list of [Node]s
 */
interface TraceToNodeTrace {
    operator fun invoke(trace: Trace): NodeTrace
}

/**
 * Accepts only events that have no lifecycle transition or are `complete`
 */
class BasicTraceToNodeTrace : TraceToNodeTrace {
    //TODO: do it right once appropriate interface is in place
    override fun invoke(trace: Trace): NodeTrace =
        trace.events
            .filter { it.lifecycleTransition == "complete" || it.lifecycleTransition == null }
            .map { Node(it.conceptName.toString(), it.conceptInstance ?: "") }
            .toList()
}