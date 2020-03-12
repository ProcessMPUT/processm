package processm.miners.heuristicminer.longdistance

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

interface LongDistanceDependencyMiner {
    fun processTrace(trace: List<Node>)
    fun mine(currentModel: Model): Collection<Pair<Node, Node>>
}