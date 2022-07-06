package processm.conformance.conceptdrift.statisticaldistance

import processm.conformance.conceptdrift.MyNormalDistribution
import processm.conformance.conceptdrift.UniformDistribution
import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import kotlin.test.Test
import kotlin.test.assertTrue

abstract class DivergenceTest {

    abstract var divergence: (ContinuousDistribution, ContinuousDistribution, Integrator) -> Double

    @Test
    fun `normal distributions with different means`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(1.0, 1.0)
        val r = MyNormalDistribution(-2.0, 1.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = divergence(q, p, integrator)
        val rp = divergence(r, p, integrator)
        assertTrue { rp > qp }
        val rq = divergence(r, q, integrator)
        val pq = divergence(p, q, integrator)
        assertTrue { rq > pq }
    }

    @Test
    fun `normal distributions with different stddevs`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = MyNormalDistribution(0.0, 2.0)
        val r = MyNormalDistribution(0.0, 8.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = divergence(q, p, integrator)
        val rp = divergence(r, p, integrator)
        assertTrue(rp > qp, "rp=$rp qp=$qp")
        val rq = divergence(r, q, integrator)
        val pq = divergence(p, q, integrator)
        assertTrue(rq > pq, "rq=$rq pq=$pq")
    }

    @Test
    fun `approximating normal with uniform`() {
        val p = MyNormalDistribution(0.0, 1.0)
        val q = UniformDistribution(-10.0, 10.0)
        val r = UniformDistribution(-3.0, 3.0)
        val integrator = MidpointIntegrator(0.001)
        val qp = divergence(q, p, integrator)
        val rp = divergence(r, p, integrator)
        // r is a better approximation, because almost all probability of the standard normal distribution is between -3 and 3
        assertTrue(rp < qp, "rp=$rp qp=$qp")
    }
}