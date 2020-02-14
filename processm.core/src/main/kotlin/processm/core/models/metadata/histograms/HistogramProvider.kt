package processm.core.models.metadata.histograms

/**
 * An interface for computing histograms
 */
interface HistogramProvider<T : Number> {
    operator fun invoke(data: Iterable<T>): Map<T, Int>
}