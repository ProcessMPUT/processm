package processm.miners.heuristicminer.bindingselectors

import processm.core.models.causalnet.Binding

interface BindingSelector<T : Binding> {
    fun reset()
    fun add(bindings: Collection<T>)
    val best: Set<T>
}