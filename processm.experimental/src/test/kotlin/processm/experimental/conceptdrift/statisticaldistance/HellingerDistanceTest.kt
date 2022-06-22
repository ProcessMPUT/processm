package processm.experimental.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.MyNormalDistribution
import processm.conformance.conceptdrift.UniformDistribution
import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.estimators.GaussianKernel
import processm.conformance.conceptdrift.estimators.KernelDensityEstimator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class HellingerDistanceTest {

    private fun HellingerDistanceForNormalDistribution(p: MyNormalDistribution, q: MyNormalDistribution) =
        HellingerDistanceForNormalDistribution(p.mean, p.stdev, q.mean, q.stdev)

    private fun HellingerDistanceForNormalDistribution(mu1: Double, sigma1: Double, mu2: Double, sigma2: Double) =
        sqrt(1 - sqrt((2 * sigma1 * sigma2) / (sigma1 * sigma1 + sigma2 * sigma2)) * exp(-0.25 * (mu1 - mu2).pow(2) / (sigma1 * sigma1 + sigma2 * sigma2)))

    @Test
    fun `normal distributions with different means`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(1.0, 1.0)
        val r = MyNormalDistribution(-2.0, 1.0)
        val integrator = MidpointIntegrator(0.01)
        val qp = HellingerDistance(q, p, integrator)
        val rp = HellingerDistance(r, p, integrator)
        assertTrue { rp > qp }
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, q), qp)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, r), rp)
        val rq = HellingerDistance(r, q, integrator)
        val pq = HellingerDistance(p, q, integrator)
        assertTrue { rq > pq }
        assertDoubleEquals(HellingerDistanceForNormalDistribution(r, q), rq)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, q), pq)
    }

    @Test
    fun `normal distributions with different stddevs`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(0.0, 2.0)
        val r = MyNormalDistribution(0.0, 8.0)
        val integrator = MidpointIntegrator(0.01)
        val qp = HellingerDistance(q, p, integrator)
        val rp = HellingerDistance(r, p, integrator)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, q), qp)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, r), rp)
        assertTrue { rp > qp }
        val rq = HellingerDistance(r, q, integrator)
        val pq = HellingerDistance(p, q, integrator)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(r, q), rq)
        assertDoubleEquals(HellingerDistanceForNormalDistribution(p, q), pq)
        assertTrue { rq > pq }
    }

    @Test
    fun `approximating normal with uniform`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = UniformDistribution(-10.0, 10.0)
        val r = UniformDistribution(-3.0, 3.0)
        val integrator = MidpointIntegrator(0.01)
        val qp = HellingerDistance(q, p, integrator)
        val rp = HellingerDistance(r, p, integrator)
        // r is a better approximation, because almost all probability of the standard normal distribution is between -3 and 3
        assertTrue { rp < qp }
    }

    @Test
    fun kde() {
        val integrator = MidpointIntegrator(0.001)
        val points = listOf(1.0, 2.0, 3.0, 4.0)
        val kde = KernelDensityEstimator(kernel = GaussianKernel, integrator = integrator).also { it.fit(points) }
        assertDoubleEquals(0.0, HellingerDistance(kde, kde, integrator))
    }

    @Test
    fun blah() {
        val integrator = MidpointIntegrator(0.001)
        val d = object : ContinuousDistribution {

            override val relevantRanges: List<ClosedFloatingPointRange<Double>> = listOf(-4.0..4.0)

            override fun pdf(x: Double): Double {
                return exp(-x * x / 2.0) / sqrt(2 * PI)
            }

        }
        assertDoubleEquals(0.0, HellingerDistance(d, d, integrator))
    }
}