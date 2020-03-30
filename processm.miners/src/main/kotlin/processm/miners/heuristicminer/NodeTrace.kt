package processm.miners.heuristicminer

import processm.core.helpers.Counter
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
            .filter { it.lifecycleTransition == "complete" }
            .map { node(it) }
            .toList()
}

class SplittingTraceToNodeTrace : TraceToNodeTrace {
    override fun invoke(trace: Trace): NodeTrace {
        val ctr = Counter<String>()
        val result = ArrayList<Node>()
        for (e in trace.events.filter { it.lifecycleTransition == "complete" }) {
            val name = e.conceptName!!
            ctr.inc(name)
            val n = if (ctr[name] >= 2) Node(name, ctr[name].toString())
            else Node(name)
            result.add(n)
        }
        return result
    }

}

