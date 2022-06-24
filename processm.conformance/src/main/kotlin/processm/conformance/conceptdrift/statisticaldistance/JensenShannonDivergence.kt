package processm.conformance.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.merge
import processm.conformance.conceptdrift.numerical.integration.Integrator
import kotlin.math.ln

private val LN2 = ln(2.0)

/**
 * Jensen-Shannon Divergence, as described in [https://en.wikipedia.org/w/index.php?title=Jensen%E2%80%93Shannon_divergence&oldid=1091202066](https://en.wikipedia.org/w/index.php?title=Jensen%E2%80%93Shannon_divergence&oldid=1091202066)
 */
fun JensenShannonDivergence(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double =
    integrator(p.relevantRanges.merge(q.relevantRanges)) { x ->
        //In theory s*ln(2) integrates to 2ln(2) and could be taken out of the integral. In practice - it does not, I suspect limited integration ranges, but didn't verify it.
        val eps = 1e-15
        val px = p.pdf(x)
        assert(px.isFinite())
        val qx = q.pdf(x)
        assert(qx.isFinite())
        val s = px + qx
        var result = 0.0
        if (px >= eps)
            result += px * ln(px)
        if (qx >= eps)
            result += qx * ln(qx)
        result -= s * (ln(s) - LN2)
        assert(result.isFinite())
        return@integrator result
    } / 2

/**
 * It seems to me that Jensen-Shannon Divergence (JSD) cannot be decomposed following the naive assumption,
 * as it employs a sum of probabilities under a logarithm, which is notoriously hard to decompose.
 * The implementation below is simply an average of JSDs for the corresponding distributions.
 *
 * TODO Average is but one way to aggregate the values. In particular maximum sounds like a viable choice, as it would make the detector more responsive
 */
fun NaiveJensenShannonDivergence(
    ps: List<ContinuousDistribution>,
    qs: List<ContinuousDistribution>,
    integrator: Integrator
): Double {
    require(ps.size == qs.size)
    if (ps.size == 1)
        return JensenShannonDivergence(ps.single(), qs.single(), integrator)
    val valid =
        (ps zip qs).filter { it.first.lowerBound.isFinite() && it.first.upperBound.isFinite() && it.second.lowerBound.isFinite() && it.second.upperBound.isFinite() }
    if (valid.isEmpty())
        return 0.0
    return valid.sumOf { (p, q) -> JensenShannonDivergence(p, q, integrator) } / valid.size
}