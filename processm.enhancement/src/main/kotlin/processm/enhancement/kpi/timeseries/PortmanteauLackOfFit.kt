package processm.enhancement.kpi.timeseries

import org.apache.commons.math3.distribution.ChiSquaredDistribution
import kotlin.math.pow

/**
 * The result of a portmanteau lack of fit test
 *
 * @param Q The statistics value, follows the Chi-Squared distribution with [df] degrees of freedom
 * @param df The number of degrees of freedom
 * @param p The p-value
 * @param n The number of observations
 *
 * @see Residuals.portmanteauLackOfFit
 */
data class PortmanteauTestResult(val Q: Double, val df: Int, val p: Double, val n: Int)

/**
 * The portmanteau lack of fit test as described in Chapter 8.2.2 of [TSAFC], based on the first [K] autocorrelations.
 * The textbook uses [K]=25 for series of 100..300 observations, at the same time indicating that this is too high,
 * especially for the sorter series of 100 observations.
 */
fun ARIMAModel.portmanteauLackOfFit(timeseries: List<Double>, K: Int): PortmanteauTestResult {
    val residuals = computeResiduals(timeseries)
    val n = residuals.size
    // Eq 8.2.3
    val Q = n * (n + 2) * (1..K).sumOf { k -> acf(residuals, k).pow(2) / (n - k) }
    val df = K - p - q
    val p = 1.0 - ChiSquaredDistribution(df.toDouble()).cumulativeProbability(Q)
    return PortmanteauTestResult(Q, df, p, n)
}