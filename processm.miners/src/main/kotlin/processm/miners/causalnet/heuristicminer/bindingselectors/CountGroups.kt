package processm.miners.causalnet.heuristicminer.bindingselectors

import processm.core.models.causalnet.Binding

/**
 * [BindingSelector] keeping bindings from a single trace together, to avoid false positives in presence of noise.
 *
 * For example, assume that there is a single trace with 1000 repetitions of a single activity and no repetitions of this
 * activity in any other trace. One may argue that these 1000 repetitions should have 1 vote instead of 1000.
 */
class CountGroups<T : Binding>(private val minSupport: Int) :
    BindingSelector<T> {

    private val counter = HashMap<Set<T>, Int>()

    override fun add(bindings: Collection<T>) {
        val tmp = bindings.toSet()
        counter[tmp] = counter.getOrDefault(tmp, 0) + 1
    }

    override val best: Set<T>
        get() = counter
            .filter { (binding, ctr) -> ctr >= minSupport }
            .keys
            .flatten()
            .toSet()

    override fun reset() {
        counter.clear()
    }
}