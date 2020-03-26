package processm.miners.heuristicminer

import processm.core.models.causalnet.Dependency
import processm.core.verifiers.causalnet.State

typealias ActiveDependencies = Set<Dependency>

/**
 * Partial replay trace, consisting of the reached state and bindings executed so far.
 */
data class ReplayTrace(
    val state: State,
    /**
     * Dependencies that were activated in the last step to reach [state], such that for each dependency its [target] is [state.a]
     */
    val joins: List<ActiveDependencies>,
    /**
     * Dependencies that were activated in the last step to reach [state], such that for each dependency its [source] is [state.a]
     */
    val splits: List<ActiveDependencies>
)