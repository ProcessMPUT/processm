package processm.core.helpers

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