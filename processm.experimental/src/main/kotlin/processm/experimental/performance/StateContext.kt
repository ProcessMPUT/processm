package processm.experimental.performance

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node

data class StateContext(
    val model: CausalNet,
    val cache: HashMap<Node, Pair<Map<Dependency, Long>, List<Pair<Join, Long>>>>
)
