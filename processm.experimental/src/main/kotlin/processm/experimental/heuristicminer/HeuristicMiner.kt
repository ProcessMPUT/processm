package processm.experimental.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.MutableCausalNet

/**
 * Common interface for heuristic miners.
 */
interface HeuristicMiner {
    /**
     * Perform mining on a given log.
     */
    fun processLog(log: Log)

    /**
     * The mined model
     */
    val result: MutableCausalNet
}