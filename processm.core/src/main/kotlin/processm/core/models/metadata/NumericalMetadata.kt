package processm.core.models.metadata


/**
 * Numerical metadata (aka statistic), offering average, median, min, max and histogram
 */
interface NumericalMetadata<T : Number, TAvg : Number> : MetadataValue {

    val mean: TAvg
    val median: TAvg
    val min: T
    val max: T
    val histogram: Map<T, Int>
}