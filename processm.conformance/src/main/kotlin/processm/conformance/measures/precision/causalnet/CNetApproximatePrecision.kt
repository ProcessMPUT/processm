package processm.conformance.measures.precision.causalnet

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

abstract class CNetApproximatePrecision(model: CausalNet) : CNetAbstractPrecision(model) {

    protected fun followSilents(nodes: Iterable<Node>, result: HashSet<Node>): Set<Node> {
        for (node in nodes)
            if (node.isSilent)
                followSilents(model.outgoing[node]?.map { it.target }.orEmpty(), result)
            else
                result.add(node)
        return result
    }
}