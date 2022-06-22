package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.numerical.integration.Integrator
import kotlin.math.max
import kotlin.math.min

fun TotalVariationDistance(p: ContinuousDistribution, q: ContinuousDistribution, integrator: Integrator): Double =
    integrator(min(p.lowerBound, q.lowerBound), max(p.upperBound, q.upperBound)) { x ->
        val px = p.pdf(x)
//        val eps = 1e-15
//        if (px < eps)
//            return@integrator 0.0
        val qx = q.pdf(x)
        return@integrator .5 * (px / qx - 1).absoluteValue
    }
