package processm.core.models.metadata.histograms

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Compute a histogram with bins of 10^logprecision width
 */
class DoubleHistogramProvider(val logprecision: Int = -1) : HistogramProvider<Double> {
    val precision: Double = 10.0.pow(logprecision)
    override fun invoke(data: Iterable<Double>): Map<Double, Int> {
        return NaiveHistogramProvider<Double>()(data.map { (it / precision).roundToInt() * precision })
    }
}