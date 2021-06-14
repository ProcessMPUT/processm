package processm.miners.onlineminer.replayer

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Split
import processm.miners.onlineminer.NodeTrace

interface Replayer {

    /**
     * Generate bindings to be added to the [model] in order to make [traces] replayable in it.
     */
    fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>>
}