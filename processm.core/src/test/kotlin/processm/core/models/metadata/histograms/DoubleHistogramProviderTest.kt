package processm.core.models.metadata.histograms

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals

class DoubleHistogramProviderTest {

    private fun compareMaps(expected: Map<Double, Int>, actual: Map<Double, Int>) {
        val eToA = expected.keys.map { e -> e to actual.keys.minBy { a -> abs(abs(a / e) - 1) } }
        assertEquals(expected.keys, eToA.map { it.first }.toSet())
        assertEquals(actual.keys, eToA.map { it.second }.toSet())
        eToA.forEach { assertEquals(expected[it.first], actual[it.second]) }
    }

    @Test
    fun singularValuesOnly() {
        val input = listOf(1.0, 2.0, 3.0)
        val result = DoubleHistogramProvider(-1)(input)
        compareMaps(mapOf(1.0 to 1, 2.0 to 1, 3.0 to 1), result)
    }

    @Test
    fun repeatingValues() {
        val input = listOf(1.0, 2.0, 3.0, 2.0, 1.0, 3.0, 3.0, 2.0, 2.0)
        val result = DoubleHistogramProvider(-1)(input)
        compareMaps(mapOf(1.0 to 2, 2.0 to 4, 3.0 to 3), result)
    }

    @Test
    fun repeatingValuesLargePrecision() {
        val input = listOf(1.0, 2.0, 4.0, 2.0, 1.0, 4.0, 4.0, 2.0, 4.0, 20.0, 20.0, 20.0, 20.0, 20.0)
        val result = DoubleHistogramProvider(0)(input)
        compareMaps(mapOf(1.0 to 2, 2.0 to 3, 4.0 to 4, 20.0 to 5), result)
    }

    @Test
    fun repeatingSmallValuesLargePrecision() {
        val input = listOf(1e-5, 2e-5, 4e-5, 2e-5, 1e-5, 4e-5, 4e-5, 2e-5, 2e-5)
        val result = DoubleHistogramProvider(-1)(input)
        compareMaps(mapOf(0.0 to 2 + 4 + 3), result)
    }


    @Test
    fun repeatingSmallValuesSmallPrecision() {
        val input = listOf(1e-5, 2e-5, 4e-5, 2e-5, 1e-5, 4e-5, 4e-5, 2e-5, 2e-5)
        val result = DoubleHistogramProvider(-5)(input)
        compareMaps(mapOf(1e-5 to 2, 2e-5 to 4, 4e-5 to 3), result)
    }

    @Test
    fun repeatingSmallValuesEvenSmallerPrecision() {
        val input = listOf(1e-5, 2e-5, 4e-5, 2e-5, 1e-5, 4e-5, 4e-5, 2e-5, 2e-5)
        val result = DoubleHistogramProvider(-7)(input)
        compareMaps(mapOf(1e-5 to 2, 2e-5 to 4, 4e-5 to 3), result)
    }


    @Test
    fun emptyInput() {
        val input = listOf<Double>()
        val result = DoubleHistogramProvider()(input)
        compareMaps(result, mapOf())
    }
}