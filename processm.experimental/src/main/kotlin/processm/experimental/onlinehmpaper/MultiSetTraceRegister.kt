package processm.experimental.onlinehmpaper

import processm.core.helpers.Counter
import processm.core.models.causalnet.Binding
import processm.experimental.miners.causalnet.heuristicminer.traceregisters.TraceRegister
import processm.core.helpers.HashMapWithDefault
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * Stores each and every trace
 */
class MultiSetTraceRegister : TraceRegister {
    protected val bindingCounter =
        HashMapWithDefault<Binding, HashSet<NodeTrace>> { HashSet() }
    private val traceCounter = Counter<NodeTrace>()

    override fun removeAll(traces: Collection<NodeTrace>) {
        for (trace in traces) {
            traceCounter.dec(trace)
        }
        bindingCounter.values.forEach { it.removeAll(traceCounter.filterValues { ctr -> ctr == 0 }.keys) }
    }

    override operator fun get(bindings: Collection<Binding>): List<NodeTrace> {
        val result = ArrayList<NodeTrace>()
        for (trace in bindings.flatMapTo(HashSet()) { bindingCounter[it] })
            for (i in 0..traceCounter[trace])
                result.add(trace)
        return result
    }

    override fun selectBest(selector: (Set<NodeTrace>) -> Boolean): Set<Binding> {
        return bindingCounter.filterValues(selector).keys
    }

    override fun register(bindings: List<Binding>, nodeTrace: NodeTrace) {
        for (binding in bindings)
            bindingCounter[binding].add(nodeTrace)
        traceCounter.inc(nodeTrace)
    }
}