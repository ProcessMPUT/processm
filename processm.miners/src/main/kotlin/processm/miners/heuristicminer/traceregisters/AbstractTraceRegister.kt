package processm.miners.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.HeuristicMiner

/**
 * An abstract trace register providing basic storage capabilities
 */
abstract class AbstractTraceRegister : TraceRegister {

    class HashMapWithDefault<K, V>(private val default: () -> V) : HashMap<K, V>() {
        override operator fun get(key: K): V {
            val result = super.get(key)
            if (result == null) {
                val new = default()
                this[key] = new
                return new
            } else {
                return result
            }
        }
    }

    protected val bindingCounter = HashMapWithDefault<Binding, HashSet<List<Node>>> { HashSet() }

    override fun removeAll(traces: Collection<List<Node>>) {
        bindingCounter.values.forEach { it.removeAll(traces) }
    }

    override operator fun get(binding: Binding): Set<List<Node>> {
        return bindingCounter[binding]
    }

    override fun selectBest(selector: (Set<List<Node>>) -> Boolean): Set<Binding> {
        return bindingCounter.filterValues(selector).keys
    }
}