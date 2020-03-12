package processm.miners.heuristicminer.avoidability

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

/**
 * An avoidability checker that always claims a dependency is avoidable
 */
class VoidAvoidabilityChecker : AvoidabilityChecker {
    override fun setContext(model: Model) {
    }

    override fun invoke(dependency: Pair<Set<Node>, Set<Node>>): Boolean {
        return true
    }
}