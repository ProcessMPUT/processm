package processm.experimental.miners.causalnet.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.helpers.HashMapWithDefault
import processm.miners.causalnet.onlineminer.NodeTrace

/**
 * An abstract trace register providing basic storage capabilities
 */
abstract class AbstractTraceRegister : TraceRegister {

    /**
     * Storage mapping bindings to relevant traces
     */
    protected val bindingCounter =
        HashMapWithDefault<Binding, HashSet<NodeTrace>> { HashSet() }

    override fun removeAll(traces: Collection<NodeTrace>) {
        bindingCounter.values.forEach { it.removeAll(traces) }
    }

    override operator fun get(bindings: Collection<Binding>): Set<NodeTrace> {
        return bindings.flatMapTo(HashSet()) { bindingCounter[it] }
    }

    override fun selectBest(selector: (Set<NodeTrace>) -> Boolean): Set<Binding> {
        return bindingCounter.filterValues(selector).keys
    }
}