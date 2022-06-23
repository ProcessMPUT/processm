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

    private val data = ArrayList<ElementImpl>()

    /**
     * The number of unique values
     */
    override val size: Int
        get() = data.size

    /**
     * The total number of values, i.e., a faster equivalent of `flatten().size`
     */
    var totalSize: Int = 0
        private set

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

    /**
     * Adds [value] to the list, first rounding it to the expected resolution.
     * If the rounded value is not present in the list, a new [Element] is added, otherwise the corresponding counter is incremented.
     */
    fun add(value: Double) {
        val x = value.roundToResolution()
        var i = data.binarySearchBy(x) { it.intValue }
        if (i < 0) {
            i = -i - 1
            data.add(i, ElementImpl(x, 1, if (i > 0) data[i - 1].cumulativeCounter else 0))
        } else {
            data[i].counter++
        }
        for (j in i until data.size)
            data[j].cumulativeCounter++
        totalSize++
    }

    /**
     * A specialized copy of [List.binarySearch] without range-checking or calling to a comparator instead of using `<` and `>`
     */
    fun insertionIndexOf(value: Double, low: Int = 0): Int {
        val x = value.roundToResolution()
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
    fun countFrom(index: Int) = totalSize - countUpToExclusive(index) //(index until size).sumOf { data[it].counter }

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
