package processm.performance.kpi

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * All data verified with R.
 */
class DistributionTests {

    @Test
    fun `distribution of zero points is invalid`() {
        val raw = doubleArrayOf()
        assertThrows<IllegalArgumentException> {
            Distribution(raw)
        }
    }

    @Test
    fun `statistics of distribution of one point`() {
        val raw = doubleArrayOf(3.14)
        val distribution = Distribution(raw)

        assertContentEquals(raw, distribution.raw)
        assertEquals(raw[0], distribution.min)
        assertEquals(raw[0], distribution.Q1)
        assertEquals(raw[0], distribution.median)
        assertEquals(raw[0], distribution.Q3)
        assertEquals(raw[0], distribution.max)
        assertEquals(0.0, distribution.standardDeviation)
        assertEquals(raw[0], distribution.quantile(0.0))
        assertEquals(raw[0], distribution.quantile(0.01))
        assertEquals(raw[0], distribution.quantile(0.33))
        assertEquals(raw[0], distribution.quantile(0.55))
        assertEquals(raw[0], distribution.quantile(0.77))
        assertEquals(raw[0], distribution.quantile(0.99))
        assertEquals(raw[0], distribution.quantile(1.0))
        assertEquals(0.0, distribution.cdf(3.0))
        assertEquals(1.0, distribution.cdf(3.14))
        assertEquals(1.0, distribution.cdf(4.0))
        assertEquals(1.0, distribution.serviceLevelAtLeast(3.0))
        assertEquals(1.0, distribution.serviceLevelAtLeast(3.14))
        assertEquals(0.0, distribution.serviceLevelAtLeast(4.0))
        assertEquals(0.0, distribution.serviceLevelAtMost(3.0))
        assertEquals(1.0, distribution.serviceLevelAtMost(3.14))
        assertEquals(1.0, distribution.serviceLevelAtMost(4.0))
    }

    @Test
    fun `statistics of distribution of two points`() {
        val raw = doubleArrayOf(1.0, 2.0)
        val distribution = Distribution(raw)

        assertContentEquals(raw, distribution.raw)
        assertEquals(distribution.quantile(0.0), distribution.min)
        assertEquals(distribution.quantile(0.25), distribution.Q1)
        assertEquals(distribution.quantile(0.5), distribution.median)
        assertEquals(distribution.quantile(0.75), distribution.Q3)
        assertEquals(distribution.quantile(1.0), distribution.max)
        assertEquals(0.707106781, distribution.standardDeviation, 1e-6)
        assertEquals(1.0, distribution.quantile(0.0))
        assertEquals(1.5, distribution.quantile(0.5))
        assertEquals(2.0, distribution.quantile(1.0))
        assertEquals(0.0, distribution.cdf(raw[0] - 0.1))
        assertEquals(0.5, distribution.cdf(raw[0]))
        assertEquals(0.5, distribution.cdf(raw[1] - 0.1))
        assertEquals(1.0, distribution.cdf(raw[1]))
        assertEquals(1.0, distribution.cdf(raw[1] + 0.1))
        assertEquals(1.0, distribution.ccdf(raw[0] - 0.1))
        assertEquals(1.0, distribution.ccdf(raw[0]))
        assertEquals(0.5, distribution.ccdf(raw[1] - 0.1))
        assertEquals(0.5, distribution.ccdf(raw[1]))
        assertEquals(0.0, distribution.ccdf(raw[1] + 0.1))
    }

    @Test
    fun `statistics of distribution of five points`() {
        val raw = doubleArrayOf(-5.0, 4.0, -3.0, 2.0, -1.0)
        val rawS = raw.sortedArray()
        val distribution = Distribution(raw)

        assertContentEquals(rawS, distribution.raw)
        assertEquals(distribution.quantile(0.0), distribution.min)
        assertEquals(distribution.quantile(0.25), distribution.Q1)
        assertEquals(distribution.quantile(0.5), distribution.median)
        assertEquals(distribution.quantile(0.75), distribution.Q3)
        assertEquals(distribution.quantile(1.0), distribution.max)
        assertEquals(3.646916506, distribution.standardDeviation, 1e-6)

        assertThrows<IllegalArgumentException> { distribution.quantile(-0.1) }
        assertEquals(-5.0, distribution.quantile(0.0))
        assertEquals(-5.0, distribution.quantile(0.1))
        assertEquals(-4.2, distribution.quantile(0.2))
        assertEquals(-3.0 - 2.0 / 3.0, distribution.quantile(0.25), 1e-6)
        assertEquals(-3.0 - 1.0 / 7.5, distribution.quantile(0.3), 1e-6)
        assertEquals(-2.0 - 1.0 / 15.0, distribution.quantile(0.4), 1e-6)
        assertEquals(-1.0, distribution.quantile(0.5))
        assertEquals(0.6, distribution.quantile(0.6), 1e-6)
        assertEquals(2.0 + 1.0 / 7.5, distribution.quantile(0.7), 1e-6)
        assertEquals(2.0 + 2.0 / 3.0, distribution.quantile(0.75), 1e-6)
        assertEquals(3.2, distribution.quantile(0.8), 1e-6)
        assertEquals(4.0, distribution.quantile(0.9))
        assertEquals(4.0, distribution.quantile(1.0))
        assertThrows<IllegalArgumentException> { distribution.quantile(1.1) }

        assertEquals(0.0, distribution.cdf(rawS[0] - 0.1))
        assertEquals(0.2, distribution.cdf(rawS[0]))
        assertEquals(0.2, distribution.cdf(rawS[1] - 0.1))
        assertEquals(0.4, distribution.cdf(rawS[1]))
        assertEquals(0.4, distribution.cdf(rawS[2] - 0.1))
        assertEquals(0.6, distribution.cdf(rawS[2]))
        assertEquals(0.6, distribution.cdf(rawS[3] - 0.1))
        assertEquals(0.8, distribution.cdf(rawS[3]))
        assertEquals(0.8, distribution.cdf(rawS[4] - 0.1))
        assertEquals(1.0, distribution.cdf(rawS[4]))
        assertEquals(1.0, distribution.cdf(rawS[4] + 0.1))
    }

    @Test
    fun `statistics of distribution of six points`() {
        val raw = doubleArrayOf(-5.0, 4.0, -3.0, 2.0, -1.0, 6.0)
        val rawS = raw.sortedArray()
        val distribution = Distribution(raw)

        assertContentEquals(rawS, distribution.raw)
        assertEquals(distribution.quantile(0.0), distribution.min)
        assertEquals(distribution.quantile(0.25), distribution.Q1)
        assertEquals(distribution.quantile(0.5), distribution.median)
        assertEquals(distribution.quantile(0.75), distribution.Q3)
        assertEquals(distribution.quantile(1.0), distribution.max)
        assertEquals(4.23083916, distribution.standardDeviation, 1e-6)

        assertThrows<IllegalArgumentException> { distribution.quantile(-0.1) }
        assertEquals(-5.0, distribution.quantile(0.0))
        assertEquals(-5.0, distribution.quantile(0.1))
        assertEquals(-3.8, distribution.quantile(0.2), 1e-6)
        assertEquals(-2.0 - 1.0 / 1.875, distribution.quantile(0.3))
        assertEquals(-1.0 - 1.0 / 3.75, distribution.quantile(0.4))
        assertEquals(0.5, distribution.quantile(0.5))
        assertEquals(2.0 + 1.0 / 3.75, distribution.quantile(0.6), 1e-6)
        assertEquals(3.0 + 1.0 / 1.875, distribution.quantile(0.7), 1e-6)
        assertEquals(4.8, distribution.quantile(0.8), 1e-6)
        assertEquals(6.0, distribution.quantile(0.9))
        assertEquals(6.0, distribution.quantile(1.0))
        assertThrows<IllegalArgumentException> { distribution.quantile(1.1) }

        assertEquals(0.0, distribution.cdf(rawS[0] - 0.1))
        assertEquals(1.0 / 6.0, distribution.cdf(rawS[0]))
        assertEquals(1.0 / 6.0, distribution.cdf(rawS[1] - 0.1))
        assertEquals(2.0 / 6.0, distribution.cdf(rawS[1]))
        assertEquals(2.0 / 6.0, distribution.cdf(rawS[2] - 0.1))
        assertEquals(3.0 / 6.0, distribution.cdf(rawS[2]))
        assertEquals(3.0 / 6.0, distribution.cdf(rawS[3] - 0.1))
        assertEquals(4.0 / 6.0, distribution.cdf(rawS[3]))
        assertEquals(4.0 / 6.0, distribution.cdf(rawS[4] - 0.1))
        assertEquals(5.0 / 6.0, distribution.cdf(rawS[4]))
        assertEquals(5.0 / 6.0, distribution.cdf(rawS[5] - 0.1))
        assertEquals(1.0, distribution.cdf(rawS[5]))
        assertEquals(1.0, distribution.cdf(rawS[5] + 0.1))
    }
}
