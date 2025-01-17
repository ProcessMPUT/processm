package processm.miners.causalnet.heuristicminer.longdistance

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node

/**
 * A plug-in component for [processm.experimental.heuristicminer.HeuristicMiner] to improve precision of the mined model.
 */
interface LongDistanceDependencyMiner {
    /**
     * Incorporate knowledge form [trace]
     */
    fun processTrace(trace: List<Node>)

    /**
     * For the model [currentModel] return dependencies that should be added to the model.
     * Each dependency is associated with the value of a dependency measure.
     * Should return an empty map if there are no dependencies to add.
     */
    fun mine(currentModel: CausalNet): Map<Dependency, Double>
}