package processm.miners.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.miners.heuristicminer.HeuristicMiner
import kotlin.math.max
import kotlin.math.min

class AdfixTraceRegister:TraceRegister {
    private val bindingCounter = HeuristicMiner.HashMapWithDefault<Binding, HashSet<List<Node>>> { HashSet() }
    private var ignoredCtr = 0

    override fun removeAll(traces:Collection<List<Node>>) {
        bindingCounter.values.forEach { it.removeAll(traces) }
    }

    override operator fun get(binding: Binding): Set<List<Node>> {
        return bindingCounter[binding]
    }

    override fun report() {
        println("IGNORED CTR = $ignoredCtr")
    }

    override fun selectBest(selector: (Set<List<Node>>) -> Boolean): Set<Binding> {
        return bindingCounter.filterValues(selector).keys
    }

    //TODO jak wybierac ktore traces mozna bezpiecznie zapomniec?
    override fun registerTrace(bindings: List<Binding>, nodeTrace: List<Node>) {
        var ignored = true
        for (binding in bindings) {
//            if (bindingCounter[binding].isNotEmpty()) {
//                if (bindingCounter[binding].any { it.size < nodeTrace.size })
//                    continue
//            }
            require(binding is Split || binding is Join)
            if (binding is Split) {
                val idx = nodeTrace.lastIndexOf(binding.source)
                assert(idx >= 0)
                val suffix = nodeTrace.subList(idx, nodeTrace.size)
                if (bindingCounter[binding].any { it.subList(max(it.size - suffix.size, 0), it.size) == suffix })
                    continue
            } else {
                val idx = nodeTrace.indexOf((binding as Join).target)
                assert(idx >= 0)
                val prefix = nodeTrace.subList(0, idx)
                if (bindingCounter[binding].any { it.subList(0, min(idx, it.size)) == prefix })
                    continue
            }
//            bindingCounter[binding].clear()
            bindingCounter[binding].add(nodeTrace)
            ignored = false
        }
        if (ignored)
            ignoredCtr += 1
    }
}