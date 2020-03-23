package processm.miners.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Node

interface TraceRegister {
    fun registerTrace(bindings: List<Binding>, nodeTrace: List<Node>)
    fun selectBest(selector: (Set<List<Node>>) -> Boolean): Set<Binding>
    fun removeAll(traces:Collection<List<Node>>)
    operator fun get(binding: Binding): Set<List<Node>>
    fun report()    //TODO remove this
}