package processm.conformance.measures

import processm.conformance.measures.precision.causalnet.CNetPerfectPrecision
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.ProcessModel

class Precision(val model: ProcessModel) : Measure<Log, Double> {

    private val base = if (model is CausalNet) CNetPerfectPrecision(model)
    else throw IllegalArgumentException("Unsupported model type ${model::class}")

    override fun invoke(artifact: Log): Double = base.invoke(artifact)
}