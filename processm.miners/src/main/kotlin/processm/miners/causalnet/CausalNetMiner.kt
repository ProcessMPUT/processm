package processm.miners.causalnet

import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.MutableCausalNet

/**
 * Common interface for Causal Net miners.
 */
interface CausalNetMiner {
    /**
     * Perform mining on a given log.
     */
    fun processLog(log: Log)

    /**
     * The mined model
     */
    val result: MutableCausalNet
}