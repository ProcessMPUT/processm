package processm.miners.causalnet

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.LogInputStream
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.commons.ProcessModel
import processm.miners.Miner

/**
 * Common interface for Causal Net miners.
 */
interface CausalNetMiner : Miner {
    override fun processLog(log: LogInputStream): ProcessModel {
        for (l in log) {
            processLog(l)
        }
        return result
    }

    /**
     * Perform mining on a given log.
     */
    fun processLog(log: Log)

    /**
     * The mined model
     */
    val result: MutableCausalNet
}
