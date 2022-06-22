package processm.conformance.conceptdrift

import kotlin.math.max

/**
 * Returns a sequence of non-overlapping [IntRange]s such that:
 * 1. The first one's first element is 0
 * 2. The last one's last element is [n]-1
 * 3. There are exactly [k] of them
 * 4. For any two consecutive [IntRange]s the first element of the second [IntRange] is the last element of the first [IntRange] + 1
 * 5. Any two [IntRange]s differ in length by at most 1
 */
fun cvFolds(k: Int, n: Int): Sequence<IntRange> = sequence {
    require(k <= n)
    val baseFoldSize = n.div(k)
    val larger = n % k
    var start = 0
    for (i in 0 until k) {
        val foldSize = baseFoldSize + (if (i < larger) 1 else 0)
        val end = start + foldSize
        yield(start until end)
        start = end
    }
    assert(start == n)
}

/**
 * Returns a sublist consisting of all elements except those at the indices indicated by [exclusionRange]
 */
fun <T> List<T>.allExcept(exclusionRange: IntRange) =
    subList(0, exclusionRange.first) + subList(exclusionRange.last + 1, size)

private class Column<T>(private val base: List<List<T>>, private val columnIndex: Int) : AbstractList<T>() {
    override val size: Int
        get() = base.size

    override fun get(index: Int): T = base[index][columnIndex]

}

/**
 * Transposes a list of lists (i.e., swaps the first and the second dimension)
 */
fun <T, L : List<T>> List<L>.transpose(): List<List<T>> = first().indices.map { Column(this, it) }

/**
 * Merges two ordered lists of disjoint ranges into a single ordered list of disjoint ranges such that:
 * 1. for any range R in one of the input lists there exists a range in the resulting list which is an improper superset R;
 * 2. for any value x if x is not a member of any range in the input lists, then it is not a member of any range in the resulting list
 */
fun List<ClosedFloatingPointRange<Double>>.merge(other: List<ClosedFloatingPointRange<Double>>): List<ClosedFloatingPointRange<Double>> {
    val result = ArrayList<ClosedFloatingPointRange<Double>>()
    var i = 0
    var j = 0
    while (i < size && j < other.size) {
        var start: Double
        var end: Double
        if (this[i].start < other[j].start) {
            start = this[i].start
            end = this[i].endInclusive
        } else {
            start = other[j].start
            end = other[j].endInclusive
        }
        var modified = true
        while (modified) {
            modified = false
            while (i < size && this[i].start <= end) {
                end = max(end, this[i++].endInclusive)
                modified = true
            }
            while (j < other.size && other[j].start <= end) {
                end = max(end, other[j++].endInclusive)
                modified = true
            }
        }
        result.add(start.rangeTo(end))
    }
    assert(i == size || j == other.size)
    if (i < size)
        result += subList(i, size)
    if (j < other.size)
        result += other.subList(j, other.size)
    return result
}