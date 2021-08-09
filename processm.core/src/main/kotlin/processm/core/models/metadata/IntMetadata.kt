package processm.core.models.metadata

import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * An implementation of [NumericalMetadata] storing Int data in a histogram
 */
class IntMetadata() : NumericalMetadata<Int, Double> {
    private var _sum: Double = 0.0
    private val _histogram = TreeMap<Int, Int>()
    private var _n = 0

    override val mean: Double
        get() = _sum / _n
    override var median: Double = 0.0
        get() = if (_n > 0) field else throw IllegalStateException()
        private set
    override var min: Int = 0
        get() = if (_n > 0) field else throw IllegalStateException()
        private set
    override var max: Int = 0
        get() = if (_n > 0) field else throw IllegalStateException()
        private set
    override val histogram: Map<Int, Int>
        get() = Collections.unmodifiableMap(_histogram)

    fun add(newData: Collection<Int>) {
        if (!newData.isEmpty()) {
            _sum += newData.sum()
            min =
                if (_n == 0) newData.minOrNull()!!
                else min(min, newData.minOrNull()!!)
            max =
                if (_n == 0) newData.maxOrNull()!!
                else max(max, newData.maxOrNull()!!)
            _n += newData.size
            _histogram.putAll(newData.groupingBy { it }.eachCount()
                .map { (k, v) -> k to _histogram.getOrDefault(k, 0) + v })
            var cumN = 0
            for (e in _histogram.entries) {
                var newCumN = cumN + e.value
                if (_n % 2 == 0) {
                    var needle = _n / 2 - 1
                    if (needle in cumN until newCumN) {
                        median = e.key.toDouble()
                    }
                    if (needle + 1 in cumN until newCumN) {
                        median = (median + e.key) / 2
                        break
                    }
                } else {
                    var needle = (_n - 1) / 2
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
