package processm.helpers

private class CollectionWrapper<T>(val sequence: Sequence<T>) : Collection<T> {
    override var size: Int = -1
        get() {
            if (field == -1) {
                field = sequence.count() // we have to count all items
            }
            return field
        }
        private set

    override fun isEmpty(): Boolean = sequence.none()

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private val seqIterator = sequence.iterator()
        private var yieldCount: Int = 0
        override fun hasNext(): Boolean {
            if (!seqIterator.hasNext()) {
                // set size
                size = yieldCount
                return false
            }
            return true
        }

        override fun next(): T {
            val next = seqIterator.next()
            yieldCount += 1
            return next
        }

    }

    override fun containsAll(elements: Collection<T>): Boolean =
        sequence.toHashSet().containsAll(elements)

    override fun contains(element: T): Boolean = sequence.contains(element)
}

/**
 * Wraps this sequence with [Collection] interface with lazily-evaluated properties. The underlying sequence must allow
 * for many iterations.
 */
fun <T> Sequence<T>.asCollection(): Collection<T> = CollectionWrapper(this)
