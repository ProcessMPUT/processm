package processm.core.models.metadata

import processm.core.models.metadata.histograms.HistogramProvider
import kotlin.math.max
import kotlin.math.min

/**
 * An implementation of [NumericalMetadata] storing everything as Double. All the added data are stored internally.
 */
class FlatDoubleMetadata() : NumericalMetadata<Double> {
    private var _sum = 0.0
    private var _median: Double = Double.NaN
    private var _min: Double = Double.NaN
    private var _max: Double = Double.NaN
    private val _data = ArrayList<Double>()

    override val average: Double
        get() = _sum / _data.size
    override val median: Double
        get() = _median
    override val min: Double
        get() = _min
    override val max: Double
        get() = _max

    fun add(newData: Collection<Double>) {
        if (!newData.isEmpty()) {
            _sum += newData.sum()
            _min =
                if (_data.isEmpty()) newData.min()!!
                else min(_min, newData.min()!!)
            _max =
                if (_data.isEmpty()) newData.max()!!
                else max(_max, newData.max()!!)
            _data += newData
            _data.sort()
            _median =
                if (_data.size % 2 == 0) (_data[_data.size / 2] + _data[_data.size / 2 - 1]) / 2
                else _data[(_data.size - 1) / 2]
        }
    }

    override fun histogram(provider: HistogramProvider<Double>): Map<Double, Int> {
        return provider(_data)
    }


}