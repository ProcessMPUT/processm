package processm.conformance.conceptdrift

import org.apache.commons.math3.distribution.AbstractRealDistribution
import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.distribution.UniformRealDistribution
import org.apache.commons.math3.random.MersenneTwister
import org.apache.commons.math3.random.RandomGenerator
import processm.conformance.alignment
import processm.conformance.models.alignments.Alignment
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.sqrt
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class DriftDetectorTestBase {

    abstract fun detector(): DriftDetector<Alignment, List<Alignment>>

    private fun `sudden univariate drift`(
        rng: RandomGenerator,
        d1: AbstractRealDistribution,
        d2: AbstractRealDistribution,
        dnoise: AbstractRealDistribution,
        pnoise: Double = 0.2,
        m: Int = 100
    ) {
        val process1 = (0..2 * m).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outlier - value from a weird distribution + misalignment
                    cost = 1
                    "a" with ("x" to dnoise.sample()) executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    "a" with ("x" to d1.sample()) executing "a"
                }
            }
        }
        val process2 = (0..m).map {
            alignment {
                // this is either an outlier or a concept drift - always misaligns, but the distribution varies
                cost = 1
                "a" with ("x" to if (rng.nextDouble() <= pnoise) dnoise.sample() else d2.sample()) executing null
            }
        }
        with(detector()) {
            fit(process1.subList(0, m))
            assertFalse(drift)
            for (a in process1.subList(m, 2 * m)) {
                observe(a)
                assertFalse(drift)
            }
            for (a in process2)
                observe(a)
            assertTrue(drift)
        }
    }


    @Test
    fun `sudden univariate drift in mean`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val d2 = NormalDistribution(rng, 1.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        `sudden univariate drift`(rng, d1, d2, dnoise)
    }

    @Ignore("This test has increased number of examples and increased noise, to ensure enough learning material. Unfortunately it takes a lot of time, so it is disabled by default")
    @Test
    fun `sudden univariate drift in variance`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val d2 = NormalDistribution(rng, 0.0, 20.0)
        val dnoise = NormalDistribution(rng, 0.0, 2.0)
        `sudden univariate drift`(rng, d1, d2, dnoise, m = 300, pnoise = 0.3)
    }

    @Ignore("This test has increased number of examples and decreased noise, as detecting the difference between two distributions with an identical mean and variance is not an easy task. It thus takes a few minutes to complete and is disabled by default")
    @Test
    fun `sudden univariate drift in shape`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 1.0, 1.0)
        assertDoubleEquals(1.0, d1.numericalMean)
        assertDoubleEquals(1.0, d1.numericalVariance)
        val d2 = UniformRealDistribution(rng, 1 - sqrt(3.0), 1 + sqrt(3.0))
        assertDoubleEquals(1.0, d2.numericalMean)
        assertDoubleEquals(1.0, d2.numericalVariance)
        val dnoise = ExponentialDistribution(rng, 1.0)
        assertDoubleEquals(1.0, dnoise.numericalMean)
        assertDoubleEquals(1.0, dnoise.numericalVariance)
        `sudden univariate drift`(rng, d1, d2, dnoise, m = 500, pnoise = 0.1)
    }

    private fun `sudden bivariate drift`(
        rng: RandomGenerator,
        d1a: AbstractRealDistribution,
        d1b: AbstractRealDistribution,
        d2a: AbstractRealDistribution,
        d2b: AbstractRealDistribution,
        dnoise: AbstractRealDistribution,
        pnoise: Double = 0.2,
        m: Int = 100
    ) {
        val process1 = (0..2 * m).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outlier - value from a weird distribution + misalignment
                    cost = 1
                    "a" with ("x" to dnoise.sample()) with ("y" to dnoise.sample()) executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    val x = d1a.sample()
                    val y = d1b.sample()
                    "a" with ("x" to x + y) with ("y" to x - y) executing "a"
                }
            }
        }
        val process2 = (0..m).map {
            alignment {
                // this is either an outlier or a concept drift - always misaligns, but the distribution varies
                cost = 1
                val x = d2a.sample()
                val y = d2b.sample()
                if (rng.nextDouble() <= pnoise)
                    "a" with ("x" to dnoise.sample()) with ("y" to dnoise.sample()) executing null
                else
                    "a" with ("x" to x + y) with ("y" to x - y) executing null
            }
        }
        with(detector()) {
            fit(process1.subList(0, m))
            assertFalse(drift)
            for (a in process1.subList(m, 2 * m)) {
                observe(a)
                assertFalse(drift)
            }
            for (a in process2)
                observe(a)
            assertTrue(drift)
        }
    }

    @Test
    fun `sudden bivariate drift in mean`() {
        val rng = MersenneTwister(42)
        val d1a = NormalDistribution(rng, 0.0, 1.0)
        val d1b = NormalDistribution(rng, 5.0, 1.0)
        val d2a = NormalDistribution(rng, 7.0, 1.0)
        val d2b = NormalDistribution(rng, 3.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.0, 1.0)
        `sudden bivariate drift`(rng, d1a, d1b, d2a, d2b, dnoise, m = 300)
    }


    @Test
    fun `sudden bivariate drift in variance`() {
        val rng = MersenneTwister(42)
        // Non-zero mean, because for mean=0 X+Y and X-Y remain independent. I don't know whether this holds for any fixed mean.
        val d1a = NormalDistribution(rng, 5.0, 1.0)
        val d1b = NormalDistribution(rng, 5.0, 3.0)
        val d2a = NormalDistribution(rng, 5.0, 13.0)
        val d2b = NormalDistribution(rng, 5.0, 27.0)
        val dnoise = NormalDistribution(rng, 5.0, 1.0)
        `sudden bivariate drift`(rng, d1a, d1b, d2a, d2b, dnoise, m = 50)
    }


    private fun `gradual drift`(
        rng: RandomGenerator,
        d1: AbstractRealDistribution,
        d2: AbstractRealDistribution,
        dnoise: AbstractRealDistribution,
        pnoise: Double = 0.2,
        n: Int = 300,
        m: Int = 100
    ) {
        val process1 = (0..n).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outiler - value from a weird distribution + misalignment
                    cost = 1
                    "a" with ("x" to dnoise.sample()) executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    "a" with ("x" to d1.sample()) executing "a"
                }
            }
        }
        val process2 = (0..n).map {
            alignment {
                // this is either an outlier or a concept drift - always misaligns, but the distribution varies
                cost = 1
                "a" with ("x" to if (rng.nextDouble() <= pnoise) dnoise.sample() else d2.sample()) executing null
            }
        }
        //mixed process skipping first m to avoid presenting the same examples as passed to fit
        val process = (0..n - m).map { if (rng.nextInt(n - m) < it) process2[it + m] else process1[it + m] }
        with(detector()) {
            // Train on process1 to avoid concept drift
            fit(process1.subList(0, m))
            // Test on mixed process
            assertFalse(drift)
            for (a in process)
                observe(a)
            assertTrue(drift)
        }
    }

    @Test
    fun `gradual drift in mean`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val d2 = NormalDistribution(rng, 1.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        `gradual drift`(rng, d1, d2, dnoise)
    }

    @Test
    fun `gradual drift in variance`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val d2 = NormalDistribution(rng, 0.0, 4.0)
        val dnoise = NormalDistribution(rng, 0.0, 2.0)
        `gradual drift`(rng, d1, d2, dnoise)
    }

    @Ignore("This test has increased number of examples and decreased noise, as detecting the difference between two distributions with an identical mean and variance is not an easy task. It thus takes a few minutes to complete and is disabled by default")
    @Test
    fun `gradual drift in shape`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 1.0, 1.0)
        assertDoubleEquals(1.0, d1.numericalMean)
        assertDoubleEquals(1.0, d1.numericalVariance)
        val d2 = UniformRealDistribution(rng, 1 - sqrt(3.0), 1 + sqrt(3.0))
        assertDoubleEquals(1.0, d2.numericalMean)
        assertDoubleEquals(1.0, d2.numericalVariance)
        val dnoise = ExponentialDistribution(rng, 1.0)
        assertDoubleEquals(1.0, dnoise.numericalMean)
        assertDoubleEquals(1.0, dnoise.numericalVariance)
        `gradual drift`(rng, d1, d2, dnoise, n = 1000, m = 500, pnoise = 0.1)
    }

    private fun nodrift(
        rng: RandomGenerator,
        d1: AbstractRealDistribution,
        dnoise: AbstractRealDistribution,
        pnoise: Double,
        n: Int = 300,
        m: Int = 100
    ) {
        val process = (0..n).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outiler - value from a weird distribution + misalignment
                    cost = 1
                    "a" with ("x" to dnoise.sample()) executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    "a" with ("x" to d1.sample()) executing "a"
                }
            }
        }
        with(detector()) {
            fit(process.subList(0, m))
            for (i in m..n) {
                observe(process[i])
                assertFalse(drift)
            }
        }
    }

    @Test
    fun `nodrift 0_1`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        nodrift(rng, d1, dnoise, 0.1)
    }

    @Test
    fun `nodrift 0_3`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        nodrift(rng, d1, dnoise, 0.3)
    }
}