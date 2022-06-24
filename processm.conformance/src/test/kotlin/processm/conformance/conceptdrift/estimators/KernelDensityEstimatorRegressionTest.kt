package processm.conformance.conceptdrift.estimators

import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.random.MersenneTwister
import org.junit.jupiter.api.Tag
import processm.conformance.conceptdrift.numerical.optimization.RMSProp
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * It is possible to verify the correctness of the parameters (bandwidth, lowerBound, upperBound) using the following Python code
 * (replacing `$bandwidth`, `$lowerBound` and `$upperBound` with concrete values, and changing kernel if needed):
 *
```
from sklearn.neighbors import KernelDensity
import numpy as np
X = np.array([-2.1, -1.3, -0.4, 1.9, 5.1, 6.2]).reshape(-1,1)
p=KernelDensity(kernel='gaussian', bandwidth=$bandwidth).fit(X)
np.sum(np.exp(p.score_samples(np.arange($lowerBound, $upperBound, 0.0001).reshape(-1,1))))*0.0001
```
 */
class KernelDensityEstimatorRegressionTest {

    @Test
    fun gaussian() {
        val points = listOf(-2.1, -1.3, -0.4, 1.9, 5.1, 6.2)
        val kdf = KernelDensityEstimator(GaussianKernel, BandwidthSelectionMethod.LEAST_SQUARES_CROSS_VALIDATION)
        kdf.optimizer = RMSProp()
        kdf.fit(points)
        val cdf = kdf.integrator(kdf.lowerBound, kdf.upperBound, kdf::pdf)
        assertTrue { cdf >= 0.99 }
        assertDoubleEquals(1.9746, kdf.bandwidth, 0.01)
    }

    @Tag("performance")
    @Test
    fun `gaussian - iterative`() {
        val points = listOf(-2.1, -1.3, -0.4, 1.9, 5.1, 6.2)
        val kdf = KernelDensityEstimator(GaussianKernel, BandwidthSelectionMethod.LEAST_SQUARES_CROSS_VALIDATION)
        kdf.optimizer = RMSProp()
        points.forEach { kdf.fit(listOf(it)) }
        assertDoubleEquals(1.9746, kdf.bandwidth, 0.01)
    }

    @Test
    fun epanechnikov() {
        val kdf = KernelDensityEstimator(EpanechnikovKernel, BandwidthSelectionMethod.LEAST_SQUARES_CROSS_VALIDATION)
        kdf.optimizer = RMSProp()
        kdf.fit(listOf(-2.1, -1.3, -0.4, 1.9, 5.1, 6.2))
        assertDoubleEquals(3.2, kdf.bandwidth, 0.01)
    }

    @Test
    fun `sum of two far away gaussians`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, -10.0, 1.0)
        val d2 = NormalDistribution(rng, 10.0, 1.0)
        val points = d1.sample(10).toList() + d2.sample(10).toList()
        val kde = KernelDensityEstimator()
        kde.fit(points)
        assertDoubleEquals(0.033, kde.pdf(-10.0))
        assertDoubleEquals(0.033, kde.pdf(10.0))
    }

    @Test
    fun `mean and standard deviation`() {
        val points = List(10) { it + 1.0 }
        val n = points.size
        val s2 = points.sumOf { it * it }
        val s1 = points.sum()
        val std = sqrt(s2 / (n - 1) - s1 / n * s1 / (n - 1))
        val kde = KernelDensityEstimator()
        kde.fit(points)
        assertDoubleEquals(s1 / n, kde.mean)
        assertDoubleEquals(std, kde.standardDeviation)
    }
}