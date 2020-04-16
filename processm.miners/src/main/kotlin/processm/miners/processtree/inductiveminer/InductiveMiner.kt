package processm.miners.processtree.inductiveminer

import processm.core.log.hierarchical.Log
import processm.core.models.processtree.Model

/**
 * Common interface for Inductive miners.
 *
 */
interface InductiveMiner {
    /**
     * Perform mining on a given log.
     */
    fun processLog(log: Log)

    /**
     * The mined model
     */
    val result: Model
}