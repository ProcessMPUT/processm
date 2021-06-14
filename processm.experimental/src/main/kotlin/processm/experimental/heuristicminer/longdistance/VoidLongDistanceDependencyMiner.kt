package processm.experimental.heuristicminer.longdistance

import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

/**
 * Long-distance miner that does nothing.
 */
class VoidLongDistanceDependencyMiner : LongDistanceDependencyMiner {
    override fun processTrace(trace: List<Node>) {
    }

    override fun mine(currentModel: CausalNet): Collection<Dependency> {
        return emptyList()
    }
}