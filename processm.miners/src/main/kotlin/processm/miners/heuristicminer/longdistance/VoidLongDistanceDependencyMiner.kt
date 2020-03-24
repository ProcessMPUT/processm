package processm.miners.heuristicminer.longdistance

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

/**
 * Long-distance miner that does nothing.
 */
class VoidLongDistanceDependencyMiner : LongDistanceDependencyMiner {
    override fun processTrace(trace: List<Node>) {
    }

    override fun mine(currentModel: Model): Collection<Pair<Node, Node>> {
        return emptyList()
    }
}