package processm.miners.heuristicminer.bindingselectors

import processm.core.models.causalnet.Binding

/**
 * [BindingSelector] counting each and every binding separately..
 *
 * For example, assume that there is a single trace with 1000 repetitions of a single activity and no repetitions of this
 * activity in any other trace. Each of these 1000 executions of a self-loop binding will be counted.
 */
class CountSeparately<T : Binding>(private val minSupport: Int) :
    BindingSelector<T> {

    private val counter = HashMap<T, Int>()

    override fun add(bindings: Collection<T>) {
        bindings.forEach { binding -> counter[binding] = counter.getOrDefault(binding, 0) + 1 }
    }

    override val best: Set<T>
        get() = counter
            .filter { (binding, ctr) -> ctr >= minSupport }
            .keys

    override fun reset() {
        counter.clear()
    }
}