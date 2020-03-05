package processm.miners.heuristicminer

import processm.core.models.causalnet.Binding

class CountSeparately<T : Binding>(private val minSupport: Int) : BindingSelector<T> {

    private val counter = HashMap<T, Int>()

    override fun add(bindings: Collection<T>) {
        bindings.forEach { binding -> counter[binding] = counter.getOrDefault(binding, 0) + 1 }
    }

    override val best: Set<T>
        get() = counter
            .filter { (binding, ctr) -> ctr >= minSupport }
            .keys
}