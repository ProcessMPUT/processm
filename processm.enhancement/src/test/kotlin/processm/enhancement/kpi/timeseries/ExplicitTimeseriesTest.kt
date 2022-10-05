package processm.enhancement.kpi.timeseries

import processm.core.log.Helpers.assertDoubleEquals
import kotlin.test.Test

class ExplicitTimeseriesTest {

    @Test
    fun `tab 7_2 - backwardApproximation`() {
        val data = listOf(-3.0, -5.0, 7.0, 3.0, -3.0, 4.0, 16.0, 14.0, -3.0)
        val (w_, a_) = ExplicitTimeseries.backwardApproximation(data, emptyList(), listOf(0.5), 0.0)
        val w = w_.takeLast(10)
        val a = a_.takeLast(10)
        //Unfortunately TSAFC gives the values only to a single decimal place
        assertDoubleEquals(1.6, a[0], 0.05)
        assertDoubleEquals(-2.2, a[1], 0.05)
        assertDoubleEquals(-6.1, a[2], 0.05)
        assertDoubleEquals(3.9, a[3], 0.05)
        assertDoubleEquals(5.0, a[4], 0.05)
        assertDoubleEquals(-0.5, a[5], 0.05)
        assertDoubleEquals(3.7, a[6], 0.05)
        assertDoubleEquals(17.9, a[7], 0.05)
        assertDoubleEquals(22.9, a[8], 0.05)
        assertDoubleEquals(8.5, a[9], 0.05)
        assertDoubleEquals(1.6, w[0], 0.05)
    }
}