package processm.miners.heuristicminer

import processm.core.models.causalnet.Node
import processm.core.verifiers.causalnet.State

data class ReplayTrace(
    val state: State,
    val joins: List<Set<Pair<Node, Node>>>,
    val splits: List<Set<Pair<Node, Node>>>
)