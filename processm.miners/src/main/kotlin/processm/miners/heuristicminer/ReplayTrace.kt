package processm.miners.heuristicminer

import processm.core.models.causalnet.Node
import processm.core.verifiers.causalnet.State

/**
 * Partial replay trace, consisting of the reached state and bindings executed so far.
 */
data class ReplayTrace(
    val state: State,
    val joins: List<Set<Pair<Node, Node>>>,
    val splits: List<Set<Pair<Node, Node>>>
)