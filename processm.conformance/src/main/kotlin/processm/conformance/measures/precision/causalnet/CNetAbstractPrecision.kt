package processm.conformance.measures.precision.causalnet

import processm.conformance.measures.precision.AbstractPrecision
import processm.conformance.models.alignments.Alignment
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity

abstract class CNetAbstractPrecision(
    override val model: CausalNet
) : AbstractPrecision(model) {

    override fun translate(alignments: Sequence<Alignment>): Sequence<List<Activity>> =
        super.translate(alignments).map { trace -> trace.map { (it as DecoupledNodeExecution).activity } }

}