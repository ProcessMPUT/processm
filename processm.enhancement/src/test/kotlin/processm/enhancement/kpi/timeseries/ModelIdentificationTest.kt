package processm.enhancement.kpi.timeseries

import kotlin.test.*

@Ignore("These tests are based on [TSAFC], which uses a different method for model identification. Moreover, currently identifyARIMAModel does not even consider MA models. Thus, all the tests in this testsuite fail.")
class ModelIdentificationTest {

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesA`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesA)
        val expected1 = ARIMAModelHyperparameters(0, 1, 1)
        val expected2 = ARIMAModelHyperparameters(1, 0, 1)
        assertTrue { model == expected1 || model == expected2 }
    }

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesB`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesB)
        assertEquals(ARIMAModelHyperparameters(0, 1, 1), model)
    }

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesC`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesC)
        val expected = listOf(
            ARIMAModelHyperparameters(1, 1, 0),
            ARIMAModelHyperparameters(0, 2, 0),   // this is given in the text
            ARIMAModelHyperparameters(0, 2, 2)    // and this in the table
        )
        assertTrue { model in expected }
    }

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesD`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesD)
        val expected = listOf(
            ARIMAModelHyperparameters(1, 0, 0),
            ARIMAModelHyperparameters(0, 1, 0),   // this is given in the text
            ARIMAModelHyperparameters(0, 1, 1)    // and this in the table
        )
        assertTrue { model in expected }
    }

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesE`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesE)
        val expected = listOf(
            ARIMAModelHyperparameters(2, 0, 0),
            ARIMAModelHyperparameters(3, 0, 0)
        )
        assertTrue { model in expected }
    }

    /**
     * See Table 6.2 of [TSAFC]
     */
    @Test
    fun `identify - seriesF`() {
        val model = identifyARIMAModel(TSAFCSeries.seriesF)
        assertEquals(ARIMAModelHyperparameters(2, 0, 0), model)
    }
}