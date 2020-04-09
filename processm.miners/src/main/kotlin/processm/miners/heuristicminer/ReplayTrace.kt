package processm.miners.heuristicminer

import org.apache.commons.collections4.MultiSet
import processm.core.models.causalnet.Dependency

typealias ActiveDependencies = Collection<Dependency>

/**
 * Partial replay trace, consisting of the reached state and bindings executed so far.
 */
data class ReplayTrace(
    val state: MultiSet<Dependency>,
    /**
     * Dependencies that were activated in the last step to reach [state], such that for each dependency its [target] is [state.a]
     */
    val joins: Iterable<ActiveDependencies>,
    /**
     * Dependencies that were activated in the last step to reach [state], such that for each dependency its [source] is [state.a]
     */
    val splits: Iterable<ActiveDependencies>
)