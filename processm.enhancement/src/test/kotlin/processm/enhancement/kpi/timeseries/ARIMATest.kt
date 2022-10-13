package processm.enhancement.kpi.timeseries


import processm.core.log.Helpers.assertDoubleEquals
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bibliography
 *
 * [TSAFC] George E. P. Box, Gwilym M. Jenkins, Gregory C. Reinsel, Greta M. Ljung: Time Series Analysis: Forecasting and Control, 5th Edition ISBN: 978-1-118-67502-1
 */
class ARIMATest {

    private fun simulateAndTest(
        process: ARIMAModel,
        prec: Double = 0.1,
        skip: Int = 1000,
        n: Int = 10000,
        error: (Int) -> Double
    ) {
        val sample = process.simulate(error).drop(skip).take(n).toList()
        val arima = ARIMA(process.phi.size, 0, 0)
        val model = arima.fit(sample)
        assertEquals(process.phi.size, model.phi.size)
        assertDoubleEquals(process.c, model.c, prec)
        for ((expected, actual) in process.phi zip model.phi)
            assertDoubleEquals(expected, actual, prec)
    }

    @Test
    fun `ar1 without constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(0.8), emptyList(), 0.0, emptyList(), emptyList())
        simulateAndTest(process) { 0.1 * rng.nextGaussian() }
    }


    @Test
    fun `ar2 without constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(0.3, 0.5), emptyList(), 0.0, emptyList(), emptyList())
        simulateAndTest(process) { 0.1 * rng.nextGaussian() }
    }

    @Test
    fun `ar2 with constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(0.3, 0.5), emptyList(), 5.0, emptyList(), emptyList())
        simulateAndTest(process) { 0.1 * rng.nextGaussian() }
    }

    @Test
    fun `ar1 with constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(listOf(0.8), emptyList(), 5.0, emptyList(), emptyList())
        simulateAndTest(process) { 0.1 * rng.nextGaussian() }
    }

    /**
     * see https://online.stat.psu.edu/stat510/lesson/2/2.1
     */
    @Test
    fun `ma1 with constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(emptyList(), listOf(-0.7), 10.0, emptyList(), emptyList())
        val sample = process.simulate { rng.nextGaussian() }.drop(100).take(1000).toList()
        val ac = (0..10).map { h -> acf(sample, h) }
        assertTrue { ac[1] >= 0.4 }
        assertTrue { ac.subList(2, ac.size).all { it.absoluteValue <= 0.1 } }
    }

    /**
     * see https://online.stat.psu.edu/stat510/lesson/2/2.1
     */
    @Test
    fun `ma2 with constant`() {
        val rng = Random(0xdead).asJavaRandom()
        val process = ARIMAModel(emptyList(), listOf(-0.5, -0.3), 10.0, emptyList(), emptyList())
        val sample = process.simulate { rng.nextGaussian() }.drop(100).take(1000).toList()
        val ac = (0..10).map { h -> acf(sample, h) }
        assertTrue { ac[1] >= 0.4 }
        assertTrue { ac[2] >= 0.2 }
        assertTrue { ac.subList(3, ac.size).all { it.absoluteValue <= 0.1 } }
    }

    @Test
    fun `TSAFC tab 7_2 - MA(1) fit`() {
        val data = listOf(-3.0, -5.0, 7.0, 3.0, -3.0, 4.0, 16.0, 14.0, -3.0)
        val arima = ARIMA(0, 0, 1)
        val model = arima.fit(data)
        val noise = model.noise
        assertEquals(data.size + 1, noise.size)
        val simulation = model.simulate { if (it in noise.indices) noise[it] else 0.0 }.drop(1).take(9).toList()
        (data zip simulation).forEach { (expected, actual) -> assertDoubleEquals(expected, actual) }
    }

    @Test
    fun `TSAFC tab 7_2 - IMA(1) fit`() {
        val data = TSAFCSeries.seriesB.subList(0, 10)
        val arima = ARIMA(0, 1, 1)
        val model = arima.fit(data)
        val noise = model.noise.takeLast(data.size)
        val simulation = model.simulate(noise::get).take(10).toList()
        assertEquals(data.toList(), simulation)
    }

}