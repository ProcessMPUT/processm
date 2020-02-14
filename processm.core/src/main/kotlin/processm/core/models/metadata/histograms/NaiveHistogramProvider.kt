package processm.core.models.metadata.histograms

/**
 * Compute histogram by placing each distinct value into a separate bin
 */
class NaiveHistogramProvider<T : Number> : HistogramProvider<T> {
    override fun invoke(data: Iterable<T>): Map<T, Int> {
        return data.groupingBy { it }.eachCount()
    }
}