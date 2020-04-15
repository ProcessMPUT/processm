package processm.miners.heuristicminer.longdistance.avoidability

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

/**
 * A purpose of an avoidibility checker is to decide whether a given dependency is already enforced by the model.
 * It is not equivalent to checking whether a dependency is present in the model, as the dependency may be indirect, e.g.,
 * in the following model a->b->c, the dependency a->c is enforced, but it is not present.
 */
interface AvoidabilityChecker {
    /**
     * Sets the model to consider. Calling [invoke] without calling [setContext] first should lead to [IllegalStateException]
     */
    fun setContext(model: CausalNet)

    /**
     * Returns true if the dependency is already enfoced by the model provided via [setContext], and false otherwise.
     */
    operator fun invoke(dependency: Pair<Set<Node>, Set<Node>>): Boolean
}