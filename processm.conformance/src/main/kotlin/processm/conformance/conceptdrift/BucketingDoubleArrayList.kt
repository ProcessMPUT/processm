package processm.conformance.conceptdrift

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

interface Element {
    val value: Double
    val counter: Int
}

class BucketingDoubleList(val log10resolution: Int = 3) : AbstractList<Element>() {

    private val resolution = 10.0.pow(log10resolution)

    private inner class ElementImpl(val intValue: Int, override var counter: Int, var cumulativeCounter: Int) : Element,
        Comparable<Int> {
        override val value: Double
            get() = intValue / resolution

        override fun compareTo(other: Int): Int = intValue.compareTo(other)
    }

    private val data = ArrayList<ElementImpl>()
    override val size: Int
        get() = data.size
    var totalSize: Int = 0
        private set

    override fun get(index: Int): Element = data[index]

    fun flatten(): List<Double> {
        //TODO make this a view
        val result = ArrayList<Double>(totalSize)
        for (element in data)
            repeat(element.counter) { result.add(element.value) }
        return result
    }

    private fun Double.roundToResolution() = (this * resolution).roundToInt()

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
//        assert(i == 0 || points[i - 1] <= points[i])
//        assert(i == points.size - 1 || points[i] <= points[i + 1]) {"i=$i points.size=${points.size} points=${points}"}
    }

    fun insertionIndexOf(value: Double, low:Int =0): Int {
        val x = value.roundToResolution()
        var low = low
        var high = data.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1) // safe from overflows
            val midVal = data[mid]

            if (x > midVal.intValue)
                low = mid + 1
            else if (x < midVal.intValue)
                high = mid - 1
            else
                return mid // key found
        }
        return low   // key not found
    }

    fun countUpToExclusive(index: Int) = if (index > 0) data[index - 1].cumulativeCounter else 0

    fun countFrom(index: Int) = totalSize - countUpToExclusive(index) //(index until size).sumOf { data[it].counter }

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
