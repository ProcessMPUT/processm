package processm.conformance.measures.precision.causalnet

import processm.conformance.measures.precision.AbstractPrecision
import processm.conformance.models.alignments.Alignment
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity

/**
 * [AbstractPrecision] accounting for some peculiarities of [CausalNet]s
 */
abstract class CNetAbstractPrecision(
    override val model: CausalNet
) : AbstractPrecision(model) {

    override fun translate(alignments: Sequence<Alignment>): Sequence<List<Activity>> =
        super.translate(alignments).map { trace -> trace.map { (it as DecoupledNodeExecution).activity } }

    protected fun followSilents(nodes: Iterable<Node>, result: HashSet<Node>): Set<Node> {
        for (node in nodes)
            if (node.isSilent)
                followSilents(model.outgoing[node]?.map { it.target }.orEmpty(), result)
            else
                result.add(node)
        return result
    }
}