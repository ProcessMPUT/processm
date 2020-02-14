package processm.core.models.metadata

import processm.core.models.metadata.histograms.HistogramProvider
import processm.core.models.metadata.histograms.NaiveHistogramProvider

/**
 * Numerical metadata (aka statistic), offering average, median, min, max and histogram
 */
interface NumericalMetadata<T : Number> : MetadataValue {

    val average: T
    val median: T
    val min: T
    val max: T
    fun histogram(provider: HistogramProvider<T> = NaiveHistogramProvider()): Map<T, Int>
}