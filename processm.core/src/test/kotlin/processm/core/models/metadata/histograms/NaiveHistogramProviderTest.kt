package processm.core.models.metadata.histograms

import org.junit.Test
import kotlin.test.assertEquals

class NaiveHistogramProviderTest {
    @Test
    fun singularValuesOnly() {
        val input = listOf(1, 2, 3)
        val result = NaiveHistogramProvider<Int>()(input)
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1), result)
    }

    @Test
    fun repeatingIntValues() {
        val input = listOf(1, 2, 3, 2, 1, 3, 3, 2, 2)
        val result = NaiveHistogramProvider<Int>()(input)
        assertEquals(mapOf(1 to 2, 2 to 4, 3 to 3), result)
    }

    @Test
    fun repeatingDoubleValues() {
        val input = listOf(1.0, 2.0, 3.0, 2.0, 1.0, 3.0, 3.0, 2.0, 2.0)
        val result = NaiveHistogramProvider<Double>()(input)
        assertEquals(mapOf(1.0 to 2, 2.0 to 4, 3.0 to 3), result)
    }

    @Test
    fun repeatingSmallDoubleValues() {
        val input = listOf(1e-5, 2e-5, 3e-5, 2e-5, 1e-5, 3e-5, 3e-5, 2e-5, 2e-5)
        val result = NaiveHistogramProvider<Double>()(input)
        assertEquals(mapOf(1e-5 to 2, 2e-5 to 4, 3e-5 to 3), result)
    }

    @Test
    fun emptyInput() {
        val input = listOf<Int>()
        val result = NaiveHistogramProvider<Int>()(input)
        assertEquals(result, mapOf())
    }
}