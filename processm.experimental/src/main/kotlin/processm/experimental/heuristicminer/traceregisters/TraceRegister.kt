package processm.experimental.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.experimental.heuristicminer.NodeTrace

/**
 * For a binding, stores a collection of somehow relevant traces
 */
interface TraceRegister {
    /**
     * Registers a trace [nodeTrace] for the give collection of bindings [bindings]
     */
    fun register(bindings: List<Binding>, nodeTrace: NodeTrace)

    /**
     * Returns bindings such that [selector] returned `true` for the collected traces corresponding to the considered binding
     */
    fun selectBest(selector: (Set<NodeTrace>) -> Boolean): Set<Binding>

    /**
     * Removes given traces from the register
     */
    fun removeAll(traces: Collection<NodeTrace>)

    /**
     * Returns the traces relevant for the binding, possibly an empty set.
     */
    operator fun get(binding: Collection<Binding>): Collection<NodeTrace>
}