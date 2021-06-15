package processm.miners.causalnet.heuristicminer.bindingselectors

import processm.core.models.causalnet.Binding

/**
 * Collects votes for bindings and returns best bindings according to these votes.
 */
interface BindingSelector<T : Binding> {
    /**
     * Clear the selector
     */
    fun reset()

    /**
     * Add bindings for a single trace
     */
    fun add(bindings: Collection<T>)

    /**
     * The best bindings
     */
    val best: Set<T>
}