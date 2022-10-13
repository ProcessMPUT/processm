package processm.enhancement.kpi.timeseries

import processm.core.log.Helpers
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.test.*

class AutocorrelationTest {

    /**
     * [TSAFC] p. 65:
     *
     * > As a numerical example, the partial autocorrelations of the AR(2) process 𝑧̃𝑡 = 0.75𝑧̃𝑡−1 − 0.50𝑧̃𝑡−2 + 𝑎𝑡 considered in Figure 3.4 are
     * > 𝜙11 = 𝜌1 = 0.5, 𝜙22 = (𝜌2 − 𝜌2 1)∕(1 − 𝜌2 1) = −0.5 ≡ 𝜙2, and 𝜙𝑘𝑘 = 0, for all 𝑘 > 2
     */
    @Test
    fun `pacf ar(2)`() {
        val rng = Random(0xdead).asJavaRandom()
        val data = ARIMAModel(listOf(0.75, -0.50), emptyList(), 0.0, emptyList(), emptyList())
            .simulate { 0.1 * rng.nextGaussian() }.drop(100).take(1000).toList()
        val pacf = (1..10).map { pacf(data, it) }
        Helpers.assertDoubleEquals(0.5, pacf[0], 0.05)
        Helpers.assertDoubleEquals(-0.5, pacf[1], 0.05)
        pacf.subList(2, pacf.size).forEach {
            Helpers.assertDoubleEquals(0.0, it, 0.05)
        }
    }

    /**
     * Low precision, because on one hand the values are only reported on a chart, on the other hand they may vary slightly due to different tools
     */
    @Test
    fun `pacf TSAFC fig 3_5`() {
        Helpers.assertDoubleEquals(-0.37, pacf(TSAFCSeries.seriesF, 1), 0.1)
        Helpers.assertDoubleEquals(0.2, pacf(TSAFCSeries.seriesF, 2), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 3), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 4), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 5), 0.1)
        Helpers.assertDoubleEquals(-0.1, pacf(TSAFCSeries.seriesF, 6), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 7), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 8), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 9), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 10), 0.1)
        Helpers.assertDoubleEquals(0.15, pacf(TSAFCSeries.seriesF, 11), 0.1)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 12), 0.1)
        Helpers.assertDoubleEquals(0.1, pacf(TSAFCSeries.seriesF, 13), 0.1)
        Helpers.assertDoubleEquals(0.15, pacf(TSAFCSeries.seriesF, 14), 0.1)
    }

    @Test
    fun `pacf series F - tab 2_1`() {
        val se = 1.0 / sqrt(TSAFCSeries.seriesF.size.toDouble())
        Helpers.assertDoubleEquals(-0.39, pacf(TSAFCSeries.seriesF, 1), se)
        Helpers.assertDoubleEquals(0.18, pacf(TSAFCSeries.seriesF, 2), se)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 3), se)
        Helpers.assertDoubleEquals(-0.04, pacf(TSAFCSeries.seriesF, 4), se)
        Helpers.assertDoubleEquals(-0.07, pacf(TSAFCSeries.seriesF, 5), se)
        Helpers.assertDoubleEquals(-0.12, pacf(TSAFCSeries.seriesF, 6), se)
        Helpers.assertDoubleEquals(0.02, pacf(TSAFCSeries.seriesF, 7), se)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 8), se)
        Helpers.assertDoubleEquals(-0.06, pacf(TSAFCSeries.seriesF, 9), se)
        Helpers.assertDoubleEquals(0.0, pacf(TSAFCSeries.seriesF, 10), se)
    }


    /**
     * see https://online.stat.psu.edu/stat510/lesson/1/1.2
     */
    @Test
    fun `acf on ar1 with positive coefficient`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(0.6), emptyList(), 0.0, emptyList(), emptyList())
        val sample = process.simulate { 0.01 * rng.nextGaussian() }.drop(1000).take(1000).toList()
        val ac = (0..12).map { h -> acf(sample, h) }
        assertTrue { ac.all { it >= 0 } }
        assertTrue { ac[1] in 0.55..0.65 }
        assertTrue { ac[2] <= 0.4 }
        assertTrue { ac[3] <= 0.3 }
        assertTrue { ac[4] <= 0.2 }
        assertTrue { ac[5] <= 0.2 }
        for (h in 6 until ac.size)
            assertTrue { ac[h] <= 0.1 }

    }

    /**
     * see https://online.stat.psu.edu/stat510/lesson/1/1.2
     */
    @Test
    fun `acf on ar1 with negative coefficient`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(-0.7), emptyList(), 0.0, emptyList(), emptyList())
        val sample = process.simulate { 0.01 * rng.nextGaussian() }.drop(1000).take(1000).toList()
        val ac = (0..12).map { h -> acf(sample, h) }
        assertTrue { ac.withIndex().all { (idx, v) -> (idx % 2 == 1) == (v < 0) } }
        assertTrue { ac[1] in -0.75..-0.65 }
        assertTrue { ac[2] in 0.45..0.55 }
        assertTrue { ac[3] in -0.45..-0.35 }
    }
}