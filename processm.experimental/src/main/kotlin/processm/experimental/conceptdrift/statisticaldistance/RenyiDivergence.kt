package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.numerical.integration.Integrator
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RenyiDivergence(val alpha: Double) {

    init {
        require(alpha >= 0)
        require(alpha != 0.0) { "Not implemented as it is a very weak measure" }
        require(alpha != 1.0) { "Use KLDivergence instead" }
        require(alpha.isFinite()) { "Not implemented as it requires computing a supremum over ratio of probabilities" }
    }

    operator fun invoke(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double =
        ln(integrator(min(p.lowerBound, q.lowerBound), max(p.upperBound, q.upperBound)) { x ->
            val px = p.pdf(x)
            val qx = q.pdf(x)
            val eps = 1e-15
            return@integrator if (px <= eps || qx <= eps)
                0.0
            else
                px.pow(alpha) * qx.pow(1 - alpha)
        }) / (alpha - 1)

    fun naive(ps: List<ContinuousDistribution>, qs: List<ContinuousDistribution>, integrator: Integrator): Double {
        require(ps.size == qs.size)
        return (ps zip qs).sumOf { (p, q) -> this(p, q, integrator) }
    }
}