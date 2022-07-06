package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.numerical.integration.Integrator
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

fun KLDivergence(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double =
    integrator(min(p.lowerBound, q.lowerBound), max(p.upperBound, q.upperBound)) { x ->
        val eps = 1e-15
        val px = p.pdf(x)
        if (px < eps)
            return@integrator 0.0
        val qx = q.pdf(x)
        return@integrator px * (ln(px) - ln(qx))
    }

/**
 * KL-Divergence for a multivariate variable consisting of univariate variables independent of each other
 *
 * This is a special case when KL-Divergence of such a joint distribution is simply a sum of KL-Divergences of marginal distributions
 */
fun NaiveKLDivergence(
    ps: List<ContinuousDistribution>,
    qs: List<ContinuousDistribution>,
    integrator: Integrator
): Double {
    require(ps.size == qs.size)
    return (ps zip qs).sumOf { (p, q) ->
        KLDivergence(p, q, integrator)
    }
}