package processm.conformance.conceptdrift

import org.apache.commons.math3.distribution.AbstractRealDistribution
import org.apache.commons.math3.distribution.ExponentialDistribution
import org.apache.commons.math3.distribution.UniformRealDistribution
import org.apache.commons.math3.random.MersenneTwister
import org.apache.commons.math3.random.RandomGenerator
import org.junit.jupiter.api.Tag
import processm.conformance.alignment
import processm.conformance.models.alignments.Alignment
import processm.core.helpers.stats.Distribution
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private interface SampleableDistribution {
    val numericalMean: Double
    val numericalVariance: Double
    fun sample(): Double
}

private class NormalDistribution(val rng: RandomGenerator, val mean: Double, val std: Double) : SampleableDistribution {
    init {
        require(std > 0)
    }

    override val numericalMean: Double
        get() = mean
    override val numericalVariance: Double
        get() = std * std

    override fun sample(): Double = std * rng.nextGaussian() + mean
}

private class Wrap(val base: AbstractRealDistribution) : SampleableDistribution {
    override val numericalMean: Double
        get() = base.numericalMean
    override val numericalVariance: Double
        get() = base.numericalVariance

    override fun sample(): Double = base.sample()

}

abstract class DriftDetectorTestBase {

    abstract fun detector(): DriftDetector<Alignment, List<Alignment>>

    private fun `sudden univariate drift`(
        rng: RandomGenerator,
        d1: SampleableDistribution,
        d2: SampleableDistribution,
        dnoise: SampleableDistribution,
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

    @Test
    fun `sudden univariate drift in variance`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val d2 = NormalDistribution(rng, 0.0, 20.0)
        val dnoise = NormalDistribution(rng, 0.0, 2.0)
        `sudden univariate drift`(rng, d1, d2, dnoise, m = 300, pnoise = 0.3)
    }

    @Test
    fun `sudden univariate drift in shape`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 1.0, 1.0)
        assertDoubleEquals(1.0, d1.numericalMean)
        assertDoubleEquals(1.0, d1.numericalVariance)
        val d2 = Wrap(UniformRealDistribution(rng, 1 - sqrt(3.0), 1 + sqrt(3.0)))
        assertDoubleEquals(1.0, d2.numericalMean)
        assertDoubleEquals(1.0, d2.numericalVariance)
        val dnoise = Wrap(ExponentialDistribution(rng, 1.0))
        assertDoubleEquals(1.0, dnoise.numericalMean)
        assertDoubleEquals(1.0, dnoise.numericalVariance)
        `sudden univariate drift`(rng, d1, d2, dnoise, m = 500, pnoise = 0.1)
    }

    private interface MultinomialSamplable {
        val size: Int
        fun sample(): List<Double>
    }

    private class Mixture(val distributions: List<SampleableDistribution>, val weights: List<List<Double>>) :
        MultinomialSamplable {

        init {
            require(weights.all { it.size == size })
        }

        override val size: Int
            get() = distributions.size

        override fun sample(): List<Double> {
            val base = distributions.map(SampleableDistribution::sample)
            return weights.map { varWeights ->
                (varWeights zip base).sumOf { (w, s) -> w * s }
            }
        }

        fun estimate(n: Int = 10000): List<Pair<Double, Double>> {
            val samples = (1..n).map { sample() }
            return (0 until size).map { i ->
                val d = Distribution(samples.map { it[i] })
                d.average to d.standardDeviation
            }
        }
    }

    private fun `sudden multivariate drift`(
        rng: RandomGenerator,
        d1: MultinomialSamplable,
        d2: MultinomialSamplable,
        dnoise: MultinomialSamplable,
        pnoise: Double = 0.2,
        m: Int = 100,
        hasDrift: Boolean = true
    ) {
        val n = d1.size
        require(n == d2.size)
        require(n == dnoise.size)
        val process1 = (0..2 * m).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outlier - value from a weird distribution + misalignment
                    cost = 1
                    dnoise.sample().foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    d1.sample().foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing "a"
                }
            }
        }
        val process2 = (0..m).map {
            alignment {
                // this is either an outlier or a concept drift - always misaligns, but the distribution varies
                cost = 1
                (if (rng.nextDouble() <= pnoise) dnoise else d2).sample()
                    .foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing null
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
            assertEquals(hasDrift, drift)
        }
    }

    private fun `gradual multivariate drift`(
        rng: RandomGenerator,
        d1: MultinomialSamplable,
        d2: MultinomialSamplable,
        dnoise: MultinomialSamplable,
        pnoise: Double = 0.2,
        n: Int = 300,
        m: Int = 100
    ) {
        val process1 = (0..n).map {
            alignment {
                if (rng.nextDouble() <= pnoise) {
                    //this is an outiler - value from a weird distribution + misalignment
                    cost = 1
                    dnoise.sample().foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing null
                } else {
                    // this is the baseline process - aligns + has the value from the baseline distribution
                    cost = 0
                    d1.sample().foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing "a"
                }
            }
        }
        val process2 = (0..n).map {
            alignment {
                // this is either an outlier or a concept drift - always misaligns, but the distribution varies
                cost = 1
                (if (rng.nextDouble() <= pnoise) dnoise else d2).sample()
                    .foldIndexed("a".asEvent()) { i, a, x -> a with ("x$i" to x) } executing null
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

    private fun `sudden bivariate drift`(
        rng: RandomGenerator,
        d1a: SampleableDistribution,
        d1b: SampleableDistribution,
        d2a: SampleableDistribution,
        d2b: SampleableDistribution,
        dnoise: SampleableDistribution,
        pnoise: Double = 0.2,
        m: Int = 100
    ) = `sudden multivariate drift`(
        rng,
        Mixture(listOf(d1a, d1b), listOf(listOf(1.0, 1.0), listOf(1.0, -1.0))),
        Mixture(listOf(d2a, d2b), listOf(listOf(1.0, 1.0), listOf(1.0, -1.0))),
        Mixture(listOf(dnoise, dnoise), listOf(listOf(1.0, 0.0), listOf(0.0, 1.0))),
        pnoise, m
    )

    @Tag("slow")
    @Test
    fun `gradual 5variate drift in mean`() {
        val rng = MersenneTwister(42)
        val nin = 5
        val nout = 5
        val d1 = (1..nin).map { NormalDistribution(rng, it.toDouble(), 1.0) }
        val d2 = (1..nin).map { NormalDistribution(rng, 2 * it.toDouble(), 1.0) }
        val dnoise = (1..nin).map { NormalDistribution(rng, 0.0, 1.0) }
        val m = (1..nout).map { a -> (1..nin).map { b -> (a + b).toDouble() } }
        val mnoise = (1..nout).map { a -> (1..nin).map { b -> if (a == b) 1.0 else 0.0 } }
        `gradual multivariate drift`(rng, Mixture(d1, m), Mixture(d2, m), Mixture(dnoise, mnoise), m = 25, n = 75)
    }

    @Tag("slow")
    @Test
    fun `gradual 5variate drift in mean in one dimension`() {
        val rng = MersenneTwister(42)
        val nin = 5
        val nout = 5
        val d1 = (1..nin).map { NormalDistribution(rng, it.toDouble(), 1.0) }
        val d2 = (1..nin).map { NormalDistribution(rng, if (it == 1) 20.0 else it.toDouble(), 1.0) }
        val dnoise = (1..nin).map { NormalDistribution(rng, 0.0, 1.0) }
        val m = (1..nout).map { a -> (1..nin).map { b -> (a + b).toDouble() } }
        val mnoise = (1..nout).map { a -> (1..nin).map { b -> if (a == b) 1.0 else 0.0 } }
        `gradual multivariate drift`(rng, Mixture(d1, m), Mixture(d2, m), Mixture(dnoise, mnoise), m = 25, n = 75)
    }

    @Tag("slow")
    @Test
    fun `sudden 5variate drift in mean`() {
        val rng = MersenneTwister(42)
        val nin = 5
        val nout = 5
        val d1 = (1..nin).map { NormalDistribution(rng, it.toDouble(), 1.0) }
        val d2 = (1..nin).map { NormalDistribution(rng, 2 * it.toDouble(), 1.0) }
        val dnoise = (1..nin).map { NormalDistribution(rng, 0.0, 1.0) }
        val m = (1..nout).map { a -> (1..nin).map { b -> (a + b).toDouble() } }
        val mnoise = (1..nout).map { a -> (1..nin).map { b -> if (a == b) 1.0 else 0.0 } }
        `sudden multivariate drift`(rng, Mixture(d1, m), Mixture(d2, m), Mixture(dnoise, mnoise), m = 50)
    }

    @Tag("slow")
    @Test
    fun `5variate nodrift`() {
        val rng = MersenneTwister(42)
        val nin = 5
        val nout = 5
        val d1 = (1..nin).map { NormalDistribution(rng, it.toDouble(), 1.0) }
        val dnoise = (1..nin).map { NormalDistribution(rng, 0.0, 1.0) }
        val m = (1..nout).map { a -> (1..nin).map { b -> (a + b).toDouble() } }
        val mnoise = (1..nout).map { a -> (1..nin).map { b -> if (a == b) 1.0 else 0.0 } }
        val s = Mixture(d1, m)
        `sudden multivariate drift`(rng, s, s, Mixture(dnoise, mnoise), m = 50, hasDrift = false)
    }

    @Tag("slow")
    @Test
    fun `sudden 5variate drift in mean in one dimension`() {
        val rng = MersenneTwister(42)
        val nin = 5
        val nout = 5
        val d1 = (1..nin).map { NormalDistribution(rng, it.toDouble(), 1.0) }
        val d2 = (1..nin).map { NormalDistribution(rng, (if (it == 1) 20.0 else it.toDouble()), 1.0) }
        val dnoise = (1..nin).map { NormalDistribution(rng, 0.0, 1.0) }
        val m = (1..nout).map { a -> (1..nin).map { b -> (a + b).toDouble() } }
        val mnoise = (1..nout).map { a -> (1..nin).map { b -> if (a == b) 1.0 else 0.0 } }
        `sudden multivariate drift`(rng, Mixture(d1, m), Mixture(d2, m), Mixture(dnoise, mnoise))
    }

    @Test
    fun `sudden bivariate drift in mean`() {
        val rng = MersenneTwister(42)
        val d1a = NormalDistribution(rng, 0.0, 1.0)
        val d1b = NormalDistribution(rng, 5.0, 1.0)
        val d2a = NormalDistribution(rng, 7.0, 1.0)
        val d2b = NormalDistribution(rng, 3.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.0, 1.0)
        `sudden bivariate drift`(rng, d1a, d1b, d2a, d2b, dnoise, m = 50)
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
        d1: SampleableDistribution,
        d2: SampleableDistribution,
        dnoise: SampleableDistribution,
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

    @Test
    fun `gradual drift in shape`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 1.0, 1.0)
        assertDoubleEquals(1.0, d1.numericalMean)
        assertDoubleEquals(1.0, d1.numericalVariance)
        val d2 = Wrap(UniformRealDistribution(rng, 1 - sqrt(3.0), 1 + sqrt(3.0)))
        assertDoubleEquals(1.0, d2.numericalMean)
        assertDoubleEquals(1.0, d2.numericalVariance)
        val dnoise = Wrap(ExponentialDistribution(rng, 1.0))
        assertDoubleEquals(1.0, dnoise.numericalMean)
        assertDoubleEquals(1.0, dnoise.numericalVariance)
        `gradual drift`(rng, d1, d2, dnoise, n = 1000, m = 500, pnoise = 0.1)
    }

    private fun nodrift(
        rng: RandomGenerator,
        d1: SampleableDistribution,
        dnoise: SampleableDistribution,
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
                assertFalse(observe(process[i]))
            }
        }
    }

    @Test
    fun `nodrift 0_1`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        nodrift(rng, d1, dnoise, 0.1, m = 300, n = 600)
    }

    @Test
    fun `nodrift 0_3`() {
        val rng = MersenneTwister(42)
        val d1 = NormalDistribution(rng, 0.0, 1.0)
        val dnoise = NormalDistribution(rng, 0.5, 1.0)
        nodrift(rng, d1, dnoise, 0.3)
    }
}