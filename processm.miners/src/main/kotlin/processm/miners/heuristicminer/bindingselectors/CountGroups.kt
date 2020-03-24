package processm.miners.heuristicminer.bindingselectors

import processm.core.models.causalnet.Binding

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