package processm.miners.heuristicminer.traceregisters

import processm.core.models.causalnet.Binding
import processm.miners.heuristicminer.NodeTrace

/**
 * An abstract trace register providing basic storage capabilities
 */
abstract class AbstractTraceRegister : TraceRegister {

    /**
     * Map with [get] behaving like [getOrPut].
     *
     * Cannot call [getOrPut] directly, as it is an extension function which calls [get]
     */
    protected class HashMapWithDefault<K, V>(private val default: () -> V) : HashMap<K, V>() {
        override operator fun get(key: K): V {
            val result = super.get(key)
            return if (result == null) {
                val new = default()
                this[key] = new
                new
            } else
                result
        }
    }

    /**
     * Storage mapping bindings to relevant traces
     */
    protected val bindingCounter = HashMapWithDefault<Binding, HashSet<NodeTrace>> { HashSet() }

    override fun removeAll(traces: Collection<NodeTrace>) {
        bindingCounter.values.forEach { it.removeAll(traces) }
    }

    override operator fun get(binding: Binding): Set<NodeTrace> {
        return bindingCounter[binding]
    }

    override fun selectBest(selector: (Set<NodeTrace>) -> Boolean): Set<Binding> {
        return bindingCounter.filterValues(selector).keys
    }
}