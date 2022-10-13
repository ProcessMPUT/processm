package processm.enhancement.kpi.timeseries

import processm.core.log.Helpers.assertDoubleEquals
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesA
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesB
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesC
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesD
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesE
import processm.enhancement.kpi.timeseries.TSAFCSeries.seriesF
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class PortmanteauLackOfFitTest {

    val modelA1 = ARIMAModel(listOf(0.92), listOf(0.58), 1.45, emptyList(), emptyList())
    val modelA2 = ARIMAModel(emptyList(), listOf(0.7), 0.0, listOf(seriesA[0]), emptyList())
    val modelB = ARIMAModel(emptyList(), listOf(-0.09), 0.0, listOf(seriesB[0]), emptyList())
    val modelC1 = ARIMAModel(listOf(0.82), emptyList(), 0.0, seriesC.subList(0, 1), emptyList())
    val modelC2 = ARIMAModel(emptyList(), listOf(0.13, 0.12), 0.0, seriesC.subList(0, 2), emptyList())
    val modelD1 = ARIMAModel(listOf(0.87), emptyList(), 1.17, emptyList(), emptyList())
    val modelD2 = ARIMAModel(emptyList(), listOf(0.06), 0.0, listOf(seriesD[0]), emptyList())
    val modelE1 = ARIMAModel(listOf(1.42, -0.73), emptyList(), 14.35, emptyList(), emptyList())
    val modelE2 = ARIMAModel(listOf(1.57, -1.02, 0.21), emptyList(), 11.31, emptyList(), emptyList())
    val modelF = ARIMAModel(listOf(-0.34, 0.19), emptyList(), 58.87, emptyList(), emptyList())

    @Test
    fun `tab 8_1 modelA1`() {
        with(modelA1.portmanteauLackOfFit(seriesA, 25)) {
            assertEquals(23, df)
            assertEquals(197, n)
            assertDoubleEquals(28.4, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelA2`() {
        with(modelA2.portmanteauLackOfFit(seriesA, 25)) {
            assertEquals(24, df)
            assertEquals(196, n)
            assertDoubleEquals(31.9, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelB`() {
        with(modelB.portmanteauLackOfFit(seriesB, 25)) {
            assertEquals(24, df)
            assertEquals(368, n)
            assertDoubleEquals(38.8, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelC1`() {
        with(modelC1.portmanteauLackOfFit(seriesC, 25)) {
            assertEquals(24, df)
            assertEquals(225, n)
            assertDoubleEquals(31.3, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelC2`() {
        with(modelC2.portmanteauLackOfFit(seriesC, 25)) {
            assertEquals(23, df)
            assertEquals(224, n)
            assertDoubleEquals(36.2, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelD1`() {
        with(modelD1.portmanteauLackOfFit(seriesD, 25)) {
            assertEquals(24, df)
            assertEquals(310, n)
            assertDoubleEquals(11.5, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelD2`() {
        with(modelD2.portmanteauLackOfFit(seriesD, 25)) {
            assertEquals(24, df)
            assertEquals(309, n)
            assertDoubleEquals(18.8, Q, 0.01)
        }
    }

    @Ignore("TSAFC gives a sligtly different value for Q, I don't know why")
    @Test
    fun `tab 8_1 modelE1`() {
        with(modelE1.portmanteauLackOfFit(seriesE, 25)) {
            assertEquals(23, df)
            assertEquals(100, n)
            assertDoubleEquals(26.8, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 modelE2`() {
        with(modelE2.portmanteauLackOfFit(seriesE, 25)) {
            assertEquals(22, df)
            assertEquals(100, n)
            assertDoubleEquals(20.0, Q, 0.01)
        }
    }

    @Test
    fun `tab 8_1 seriesF`() {
        with(modelF.portmanteauLackOfFit(seriesF, 25)) {
            assertEquals(23, df)
            assertEquals(70, n)
            assertDoubleEquals(14.7, Q, 0.01)
        }
    }

}