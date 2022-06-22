package processm.conformance.conceptdrift.statisticaldistance

import org.apache.commons.math3.random.MersenneTwister
import processm.conformance.conceptdrift.estimators.ContinuousDistribution
import processm.conformance.conceptdrift.estimators.KernelDensityEstimator
import processm.conformance.conceptdrift.numerical.integration.Integrator
import processm.conformance.conceptdrift.numerical.integration.MidpointIntegrator
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class JensenShannonDivergenceTest : DivergenceTest() {
    override var divergence: (ContinuousDistribution, ContinuousDistribution, Integrator) -> Double =
        ::JensenShannonDivergence

    @Ignore("This is not a test, but it shows that the distance is not necessarily non-decreasing as we add more points to only one of the distributions")
    @Test
    fun `is nondecreasing`() {
        val p = KernelDensityEstimator()
        val q = KernelDensityEstimator()
        val rng = MersenneTwister(0xbadd09)
        val integrator = MidpointIntegrator(0.001)
        val data1 = List(100) { rng.nextGaussian() }
        val data2 = List(100) { rng.nextGaussian() + 10 }
        val data3 = List(100) { rng.nextGaussian() }
        p.fit(data1)
        q.fit(data1)
        assertDoubleEquals(0.0, divergence(p, q, integrator))

        p.fit(data2)
        val d1 = divergence(p, q, integrator)

        p.fit(data3)
        val d2 = divergence(p, q, integrator)

        assertTrue(d2 < d1, "Divergence before: $d1 after: $d2")
    }
}