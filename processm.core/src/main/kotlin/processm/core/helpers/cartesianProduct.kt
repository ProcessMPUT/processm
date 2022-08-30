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
fun <T> List<List<T>>.cartesianProduct(): Sequence<List<T>> = sequence {
    class ResettableListIterator<T>(private val base: List<T>) : Iterator<T> {
        private var idx = 0
        override fun hasNext(): Boolean = idx < base.size

        override fun next(): T = base[idx++]

        fun reset() {
            idx = 0
        }
    }

    val iterators = this@cartesianProduct.mapTo(ArrayList()) { ResettableListIterator(it) }
    val current = iterators.mapTo(ArrayList()) { it.next() }
    while (true) {
        yield(ArrayList(current))   //return a copy
        var finished = true
        for (idx in 0 until iterators.size) {
            if (iterators[idx].hasNext()) {
                current[idx] = iterators[idx].next()
                finished = false
                break
            } else {
                iterators[idx].reset()
                current[idx] = iterators[idx].next()
            }
        }
        if (finished)
            break
    }
}