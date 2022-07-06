package processm.conformance.conceptdrift

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A data structure employed by [BucketingDoubleList]
 */
interface Element {
    /**
     * The stored value
     */
    val value: Double

    /**
     * The number of occurrences
     */
    val counter: Int
}

/**
 * A list of [Double]s such that each value is rounded to [log10resolution] decimal places, and that the values are stored
 * as pairs: value and number of occurrences (using [Element]).
 *
 * The main purpose is to explicitly enable computing `n*f(x)` instead of `f(x)+f(x)+...+f(x)`,
 * and approximating computing `f(x)+f(x+eps)` by `2*f(x)`.
 */
class BucketingDoubleList(val log10resolution: Int = 3) : AbstractList<Element>() {

    private val resolution = 10.0.pow(log10resolution)

    private inner class ElementImpl(val intValue: Int, override var counter: Int, var cumulativeCounter: Int) : Element,
        Comparable<Int> {
        override val value: Double
            get() = intValue / resolution

        override fun compareTo(other: Int): Int = intValue.compareTo(other)
    }

    private var data = ArrayList<ElementImpl>()

    /**
     * The number of unique values
     */
    override val size: Int
        get() = data.size

    /**
     * The total number of values, i.e., a faster equivalent of `flatten().size`
     */
    val totalSize: Int
        get() = if (data.isNotEmpty()) data.last().cumulativeCounter else 0

    override fun get(index: Int): Element = data[index]

    /**
     * Returns an ordered list of values contained in the container. Every value is repeated as per [Element.counter]
     */
    fun flatten(): List<Double> {
        //TODO This should be a view, not a copy, but currently the only usage is in KernelDensityEstimator.silverman, and this does not warrant the effort
        val result = ArrayList<Double>(totalSize)
        for (element in data)
            repeat(element.counter) { result.add(element.value) }
        return result
    }

    private fun Double.roundToResolution() = (this * resolution).roundToInt()

    private fun makeNewElementList(values: Collection<Double>): List<ElementImpl> {
        val sorted = values.mapTo(ArrayList()) { it.roundToResolution() }.also { it.sort() }
        val result = ArrayList<ElementImpl>(sorted.size)
        var previous: ElementImpl? = null
        for (value in sorted.sorted()) {
            if (previous?.intValue != value) {
                val element = ElementImpl(value, 1, (previous?.cumulativeCounter ?: 0) + 1)
                result.add(element)
                previous = element
            } else {
                previous.counter++
                previous.cumulativeCounter++
            }
        }
        assert(result.last().cumulativeCounter == values.size)
        return result
    }

    /**
     * Adds [value] to the list, first rounding it to the expected resolution.
     * If the rounded value is not present in the list, a new [Element] is added, otherwise the corresponding counter is incremented.
     *
     * Let `n` denote [size] and `m` denote [values.size]. [addAll] takes `O(m*log(m) + (m+n))`, where `O(m*log(m))`
     * is the cost of sorting [values] and `O(m+n)` is the cost of merging [data] and sorted [values]
     */
    fun addAll(values: Collection<Double>) {
        if (values.isEmpty())
            return
        val toAdd = makeNewElementList(values)
        var i = insertionIndexOf(toAdd.first().intValue)
        var prevCumulativeCounter = countUpToExclusive(i)
        var j = 0
        val result = ArrayList<ElementImpl>(data.size + toAdd.size)
        result.addAll(data.subList(0, i))
        while (i < data.size && j < toAdd.size) {
            when {
                data[i].intValue == toAdd[j].intValue -> {
                    data[i].counter += toAdd[j].counter
                    result.add(data[i])
                    i++
                    j++
                }
                data[i].intValue < toAdd[j].intValue -> {
                    result.add(data[i++])
                }
                else -> {
                    assert(data[i].intValue > toAdd[j].intValue)
                    result.add(toAdd[j++])
                }
            }
            with(result.last()) {
                prevCumulativeCounter += counter
                cumulativeCounter = prevCumulativeCounter
            }
        }
        assert(i == data.size || j == toAdd.size)
        while (j < toAdd.size) {
            result.add(toAdd[j++])
            with(result.last()) {
                prevCumulativeCounter += counter
                cumulativeCounter = prevCumulativeCounter
            }
        }
        while (i < data.size) {
            result.add(data[i++])
            with(result.last()) {
                prevCumulativeCounter += counter
                cumulativeCounter = prevCumulativeCounter
            }
        }
        data = result
    }

    /**
     * A specialized copy of [List.binarySearch] without range-checking or calling to a comparator instead of using `<` and `>`
     * Always returns a non-negative index, indicating the position of x, or the position x should be inserted to if x is absent
     */
    fun insertionIndexOf(value: Double, low: Int = 0): Int = insertionIndexOf(value.roundToResolution(), low)

    private fun insertionIndexOf(x: Int, low: Int = 0): Int {
        var low = low
        var high = data.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1) // safe from overflows
            val midVal = data[mid]

            when {
                x > midVal.intValue -> low = mid + 1
                x < midVal.intValue -> high = mid - 1
                else -> return mid  // key found
            }
        }
        return low   // key not found
    }

    /**
     * Returns the total number of values in the list before the [index], i.e., the sum of [Element.counter] for indexes `0 until index`
     */
    fun countUpToExclusive(index: Int) = if (index > 0) data[index - 1].cumulativeCounter else 0

    /**
     * Returns the total number of values in the list from [index] onwards, i.e., the sum of [Element.counter] for indexes `index until size`
     */
    fun countFrom(index: Int) = totalSize - countUpToExclusive(index)

    /**
     * Returns an ordered list of disjoint ranges such that for any value `x` present in the list:
     * 1. There is exactly one range `[a,b]` such that `x ∈ [a,b]`
     * 2. a <= x-[left]
     * 3. x+[right] <= b
     *
     * Moreover, for any range `[a,b]`, for all values `x1, ..., xm` from the list such that  `x1, ..., xm ∈ [a,b]`:
     * 1. a = min(x1, ..., xm)-[left]
     * 2. b = max(x1, ..., xm)+[right]
     */
    fun relevantRanges(left: Double, right: Double): List<ClosedFloatingPointRange<Double>> {
        val result = ArrayList<ClosedFloatingPointRange<Double>>()
        if (data.isNotEmpty()) {
            var start = data[0].value - left
            var end = data[0].value + right
            for (d in data) {
                val newStart = d.value - left
                if (newStart >= end) {
                    result.add(start.rangeTo(end))
                    start = newStart
                }
                end = d.value + right
            }
            result.add(start.rangeTo(end))
        }
        return result
    }
}
