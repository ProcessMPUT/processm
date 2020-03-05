package processm.miners.heuristicminer

import processm.core.models.causalnet.Binding

interface BindingSelector<T : Binding> {
    fun add(bindings: Collection<T>)
    val best: Set<T>
}