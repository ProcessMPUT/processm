package processm.miners

import processm.core.log.hierarchical.LogInputStream
import processm.core.models.commons.ProcessModel

/**
 * The base interface for discovery algorithms for process models.
 */
interface Miner {
    /**
     * Processes the given [log] to produce a [ProcessModel].
     */
    fun processLog(log: LogInputStream): ProcessModel
}
