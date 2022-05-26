package processm.conformance.conceptdrift

import org.apache.commons.math3.distribution.NormalDistribution
import processm.conformance.conceptdrift.estimators.ContinousDistribution
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import kotlin.test.Test
import kotlin.test.assertTrue

private class MyNormalDistribution(mean: Double, stdev: Double) : ContinousDistribution {

    private val nd = NormalDistribution(mean, stdev)

    override val lowerBound: Double = mean - 6 * stdev
    override val upperBound: Double = mean + 6 * stdev

    override fun pdf(x: Double): Double = nd.density(x)
}

private class UniformDistribution(override val lowerBound: Double, override val upperBound: Double) :
    ContinousDistribution {
    override fun pdf(x: Double): Double = 1.0 / (upperBound - lowerBound)
}

class KLDivergenceTest {
    @Test
    fun `normal distributions with different means`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(1.0, 1.0)
        val r = MyNormalDistribution(-2.0, 1.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = KLDivergence(q, p, integrator)
        val rp = KLDivergence(r, p, integrator)
        assertTrue { rp > qp }
        val rq = KLDivergence(r, q, integrator)
        val pq = KLDivergence(p, q, integrator)
        assertTrue { rq > pq }
    }

    @Test
    fun `normal distributions with different stddevs`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(0.0, 2.0)
        val r = MyNormalDistribution(0.0, 4.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = KLDivergence(q, p, integrator)
        val rp = KLDivergence(r, p, integrator)
        assertTrue { rp > qp }
        val rq = KLDivergence(r, q, integrator)
        val pq = KLDivergence(p, q, integrator)
        assertTrue { rq > pq }
    }

    @Test
    fun `approximating normal with uniform`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = UniformDistribution(-10.0, 10.0)
        val r = UniformDistribution(-3.0, 3.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = KLDivergence(q, p, integrator)
        val rp = KLDivergence(r, p, integrator)
        // r is a better approximation, because almost all probability of the standard normal distribution is between -3 and 3
        assertTrue { rp < qp }
    }
}