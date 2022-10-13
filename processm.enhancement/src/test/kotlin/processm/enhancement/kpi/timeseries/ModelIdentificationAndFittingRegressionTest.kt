package processm.enhancement.kpi.timeseries

import kotlin.test.*

class ModelIdentificationAndFittingRegressionTest {

    private fun computeMSE(series: List<Double>): Double {
        val hyperparameters = identifyARIMAModel(series, slack = 1.0)
        val model = ARIMA(hyperparameters).fit(series)
        val residuals = model.computeResiduals(series)
        return residuals.sumOf { it * it } / residuals.size
    }

    @Test
    fun seriesA() {
        assertTrue { computeMSE(TSAFCSeries.seriesA) <= 0.2 }
    }

    @Test
    fun seriesB() {
        //not a good fit, but [TSAFC] reports (0,1,1) model, which is not considered
        assertTrue { computeMSE(TSAFCSeries.seriesB) <= 53 }
    }

    @Test
    fun seriesC() {
        assertTrue { computeMSE(TSAFCSeries.seriesC) <= 4.3 }
    }

    @Test
    fun seriesD() {
        assertTrue { computeMSE(TSAFCSeries.seriesD) <= 0.4 }
    }


    @Test
    fun seriesE() {
        // according to [TSAFC] nothing is really a good fit for this series
        assertTrue { computeMSE(TSAFCSeries.seriesE) <= 224 }
    }


    @Test
    fun seriesF() {
        // according to [TSAFC] nothing is really a good fit for this series
        assertTrue { computeMSE(TSAFCSeries.seriesF) <= 383 }
    }
}