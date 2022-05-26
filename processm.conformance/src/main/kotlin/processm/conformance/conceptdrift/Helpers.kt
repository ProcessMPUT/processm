package processm.conformance.conceptdrift

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