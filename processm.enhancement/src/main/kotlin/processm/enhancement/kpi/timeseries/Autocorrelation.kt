package processm.enhancement.kpi.timeseries

import kotlin.math.pow

/**
 * The autocorrelation function, as defined by Equation 2.1.12 in [TSAFC]
 *
 * @param x The timeseries
 * @param h The order of the autocorrelation to compute
 */
fun acf(x: List<Double>, h: Int): Double {
    val mean = x.average()
    val variance = x.sumOf { (it - mean).pow(2) / x.size }
    val cov = (0 until x.size - h).sumOf { (x[it] - mean) * (x[it + h] - mean) / x.size }
    return cov / variance
}

/**
 * The partial autocorrelation function
 *
 * @param x The timeseries
 * @param h The order of the partial autocorrelation to compute
 */
fun pacf(x: List<Double>, h: Int): Double = ARIMA(h, 0, 0).fit(x).phi.last()

