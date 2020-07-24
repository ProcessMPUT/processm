package processm.miners.heuristicminer.windowing

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Split
import processm.miners.heuristicminer.NodeTrace

interface Replayer {

    fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>>
}