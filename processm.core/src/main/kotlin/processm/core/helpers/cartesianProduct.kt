package processm.core.helpers

/**
 * Returns a sequence with cartesian product of all given [Iterable]s, e.g.,
 * ```
 * listOf(listOf('a', 'b', 'c'), listOf(1, 2)).cartesianProduct()
 * ```
 * yields a sequence of the following items:
 * ```
 * listOf('a', 1)
 * listOf('b', 1)
 * listOf('c', 1)
 * listOf('a', 2)
 * listOf('b', 2)
 * listOf('c', 2)
 * ```
 */
fun <T> List<Iterable<T>>.cartesianProduct(): Sequence<List<T>> = sequence {
    val iterators = this@cartesianProduct.map { it.iterator() }.toMutableList()
    val current = iterators.map { it.next() }.toMutableList()
    while (true) {
        yield(ArrayList(current))   //return a copy
        var finished = true
        for (idx in 0 until iterators.size) {
            if (iterators[idx].hasNext()) {
                current[idx] = iterators[idx].next()
                finished = false
                break
            } else {
                iterators[idx] = this@cartesianProduct[idx].iterator()
                current[idx] = iterators[idx].next()
            }
        }
        if (finished)
            break
    }
}