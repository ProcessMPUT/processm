package processm.core.models.metadata

import processm.core.helpers.stats.DescriptiveStatistics


/**
 * Numerical metadata (aka statistic), offering average, median, min, max and histogram
 */
interface NumericalMetadata<T : Number, TAvg : Number> : MetadataValue, DescriptiveStatistics<T, TAvg> {

    val histogram: Map<T, Int>
}