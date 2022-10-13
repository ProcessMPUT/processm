package processm.enhancement.kpi.timeseries

import kotlin.test.*

class TSAFCSeriesTest() {
    @Test
    fun seriesA() {
        val s = TSAFCSeries.seriesA
        assertEquals(197, s.size)
        assertEquals(listOf(17.0, 16.6, 16.3, 16.1, 17.1), s.subList(0, 5))
        assertEquals(listOf(17.6, 17.8, 17.7, 17.2, 17.4), s.subList(s.size - 5, s.size))
    }

    @Test
    fun seriesB() {
        val s = TSAFCSeries.seriesB
        assertEquals(369, s.size)
        assertEquals(listOf(460.0, 457.0, 452.0, 459.0, 462.0), s.subList(0, 5))
        assertEquals(listOf(345.0, 352.0, 346.0, 352.0, 357.0), s.subList(s.size - 5, s.size))
    }

    @Test
    fun seriesBprim() {
        val s = TSAFCSeries.seriesBprim
        assertEquals(255, s.size)
        assertEquals(listOf(445.0, 448.0, 450.0, 447.0, 451.0), s.subList(0, 5))
        assertEquals(listOf(525.0, 519.0, 519.0, 522.0, 522.0), s.subList(s.size - 5, s.size))
    }

    @Test
    fun seriesC() {
        val s = TSAFCSeries.seriesC
        assertEquals(226, s.size)
        assertEquals(listOf(26.6, 27.0, 27.1, 27.1, 27.1), s.subList(0, 5))
        assertEquals(listOf(19.7, 19.3, 19.1, 19.0, 18.8), s.subList(s.size - 5, s.size))
    }

    @Test
    fun seriesD() {
        val s = TSAFCSeries.seriesD
        assertEquals(310, s.size)
        assertEquals(listOf(8.0, 8.0, 7.4, 8.0, 8.0), s.subList(0, 5))
        assertEquals(listOf(8.7, 8.9, 9.1, 9.1, 9.1), s.subList(s.size - 5, s.size))
    }

    @Test
    fun seriesE() {
        val s = TSAFCSeries.seriesE
        assertEquals(100, s.size)
        assertEquals(listOf(101.0, 82.0, 66.0, 35.0, 31.0), s.subList(0, 5))
        assertEquals(listOf(30.0, 16.0, 7.0, 37.0, 74.0), s.subList(s.size - 5, s.size))
    }
}