package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.merge
import processm.conformance.conceptdrift.numerical.integration.Integrator
import kotlin.math.sqrt

fun SquaredHellingerDistance(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double {
    val integral = integrator(p.relevantRanges.merge(q.relevantRanges)) { x ->
        sqrt(p.pdf(x) * q.pdf(x))
    }
    assert(integral <= 1) { "The integral over the square root of the product of the PDFs is $integral > 1" }
    return 1 - integral
}

/**
 * H^2(f, g) = 1 - \int \sqrt{f(x)g(x)} dx
 */
fun HellingerDistance(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double =
    sqrt(SquaredHellingerDistance(p, q, integrator))

/**
 * H^2(f, g) = {I'm not sure this is a correct definition for multivariate variables}
 * 1 - \int \int \sqrt{f(x,y)g(x,y)} dy dx = {from independence}
 * 1 - \int \int \sqrt{f1(x)f2(y)g1(x)g2(y)} dy dx =
 * 1 - \int \sqrt{f1(x)g1(x)} dx \int \sqrt{f2(y)g2(y)} dy =
 * 1 - (1-H^2(f1, g1))*(1-H^2(f2, g2))
 */
fun NaiveHellingerDistance(
    ps: List<ContinuousDistribution>,
    qs: List<ContinuousDistribution>,
    integrator: Integrator
): Double {
    require(ps.size == qs.size)
    if (ps.size == 1)
        return HellingerDistance(ps.single(), qs.single(), integrator)
    var result = 1.0
    for ((p, q) in (ps zip qs))
        result *= 1 - SquaredHellingerDistance(p, q, integrator)
    return sqrt(1 - result)
}
