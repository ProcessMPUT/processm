package processm.conformance.measures

import processm.conformance.measures.precision.PerfectPrecision
import processm.conformance.measures.precision.causalnet.CNetPerfectPrecision
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * An exact value of precision, delegated to [CNetPerfectPrecision] or [PerfectPrecision] accordingly.
 */
class Precision(val model: ProcessModel) : Measure<Log, Double> {

    companion object {
        val URN = URN("urn:processm:measures/precision")
    }

    override val URN: URN
        get() = Precision.URN

    private val base = if (model is CausalNet) CNetPerfectPrecision(model)
    else PerfectPrecision(model)

    override fun invoke(artifact: Log): Double = base.invoke(artifact)
}