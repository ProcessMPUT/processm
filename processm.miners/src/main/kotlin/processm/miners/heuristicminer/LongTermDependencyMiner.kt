package processm.miners.heuristicminer

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

interface LongTermDependencyMiner {
    fun processTrace(trace: List<Node>)
    fun mine(currentModel: Model): Collection<Pair<Node, Node>>
}