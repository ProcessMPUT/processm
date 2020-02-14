package processm.core.models.metadata

import kotlin.math.max
import kotlin.math.min

/**
 * An implementation of [NumericalMetadata] storing Int data in a histogram
 */
class IntMetadata() : NumericalMetadata<Int, Double> {
    private var _sum: Double = 0.0
    private var _median: Double = 0.0
    private var _min: Int = 0
    private var _max: Int = 0
    private val _histogram = HashMap<Int, Int>()
    private var _n = 0

    override val mean: Double
        get() = _sum / _n
    override val median: Double
        get() = if (_n > 0) _median else throw IllegalStateException()
    override val min: Int
        get() = if (_n > 0) _min else throw IllegalStateException()
    override val max: Int
        get() = if (_n > 0) _max else throw IllegalStateException()
    override val histogram: Map<Int, Int>
        get() = _histogram

    fun add(newData: Collection<Int>) {
        if (!newData.isEmpty()) {
            _sum += newData.sum()
            _min =
                if (_n == 0) newData.min()!!
                else min(_min, newData.min()!!)
            _max =
                if (_n == 0) newData.max()!!
                else max(_max, newData.max()!!)
            _n += newData.size
            newData.groupingBy { it }.eachCount()
                .forEach { (k, v) -> _histogram[k] = _histogram.getOrDefault(k, 0) + v }
            var cumN = 0
            for (e in _histogram.toList().sortedBy { it.first }) {
                var newCumN = cumN + e.second
                if (_n % 2 == 0) {
                    var needle = _n / 2 - 1
                    if (needle in cumN until newCumN) {
                        _median = e.first.toDouble()
                    }
                    if (needle + 1 in cumN until newCumN) {
                        _median = (_median + e.first) / 2
                        break
                    }
                } else {
                    var needle = (_n - 1) / 2
                    if (needle in cumN until newCumN) {
                        _median = e.first.toDouble()
                        break
                    }
                }
                cumN = newCumN
            }
        }
    }


}