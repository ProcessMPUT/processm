package processm.core.models.metadata

import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An implementation of [NumericalMetadata] storing Int data in a histogram
 */
class IntMetadata() : NumericalMetadata<Int, Double> {
    private var _sum: Double = 0.0

    /**
     * Sum of the squares of [histogram] divided by [count]-1
     */
    private var _s2divn1: Double = 0.0
    private val _histogram = TreeMap<Int, Int>()
    override var count = 0
        private set

    override val average: Double
        get() = if (count > 0) _sum / count else throw IllegalStateException()

    /**
     * The corrected estimator of standard deviation of the distribution. This estimator is still biased.
     * See https://en.wikipedia.org/wiki/Standard_deviation#Corrected_sample_standard_deviation
     * and https://en.wikipedia.org/wiki/Standard_deviation#Rapid_calculation_methods
     */
    override val standardDeviation: Double
        get() = if (count > 1) sqrt(_s2divn1 - _sum / count * _sum / (count - 1)) else if (count == 1) 0.0 else throw IllegalStateException()


    /**
     * Median of the actual data points in [histogram]
     */
    override var median: Double = 0.0
        get() = if (count > 0) field else throw IllegalStateException()
        private set
    override val Q1: Double
        get() = throw UnsupportedOperationException("Computing Q1 and Q3 requires non-trivial changes to IntMetadata and it is not immediately clear that we are ever going to use it")
    override val Q3: Double
        get() = throw UnsupportedOperationException("Computing Q1 and Q3 requires non-trivial changes to IntMetadata and it is not immediately clear that we are ever going to use it")

    override var min: Int = 0
        get() = if (count > 0) field else throw IllegalStateException()
        private set
    override var max: Int = 0
        get() = if (count > 0) field else throw IllegalStateException()
        private set
    override val histogram: Map<Int, Int>
        get() = Collections.unmodifiableMap(_histogram)

    fun add(newData: Collection<Int>) {
        if (!newData.isEmpty()) {
            _sum += newData.sum()
            val newCount = count + newData.size
            _s2divn1 =
                (count - 1).toDouble() / (newCount - 1) * _s2divn1 + newData.sumOf { (it * it).toDouble() } / (newCount - 1)
            min =
                if (count == 0) newData.minOrNull()!!
                else min(min, newData.minOrNull()!!)
            max =
                if (count == 0) newData.maxOrNull()!!
                else max(max, newData.maxOrNull()!!)
            count += newData.size
            newData.groupingBy { it }.eachCount()
                .forEach { (k, v) -> _histogram.compute(k) { _, oldv -> (oldv ?: 0) + v } }
            var cumN = 0
            for (e in _histogram.entries) {
                val newCumN = cumN + e.value
                if (count % 2 == 0) {
                    val needle = count / 2 - 1
                    if (needle in cumN until newCumN) {
                        median = e.key.toDouble()
                    }
                    if (needle + 1 in cumN until newCumN) {
                        median = (median + e.key) / 2
                        break
                    }
                } else {
                    val needle = (count - 1) / 2
                    if (needle in cumN until newCumN) {
                        median = e.key.toDouble()
                        break
                    }
                }
                cumN = newCumN
            }
        }
    }


}
