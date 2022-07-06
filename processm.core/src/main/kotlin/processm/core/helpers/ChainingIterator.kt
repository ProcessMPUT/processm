package processm.core.helpers

private class ChainingIterator<T>(val base: Iterator<Iterator<T>>) : Iterator<T> {
    private var current: Iterator<T>? = null

    override fun hasNext(): Boolean {
        while (current?.hasNext() != true) {
            if (base.hasNext())
                current = base.next()
            else
                return false
        }
        return checkNotNull(current).hasNext()
    }

    override fun next(): T = checkNotNull(current).next()
}

/**
 * Returns an iterator that goes over all the elements returned by the iterators in [base]
 */
fun <T> chain(vararg base: Iterator<T>): Iterator<T> = ChainingIterator(base.iterator())

/**
 * Returns an iterator that goes over all the elements in the [Iterable]s of [base]
 */
fun <T> chain(base: Sequence<Iterable<T>>): Iterator<T> = ChainingIterator(base.map { it.iterator() }.iterator())


/**
 * Returns an iterator that goes over all the elements in the [Iterable]s of [base]
 */
fun <T> chain(vararg base: Iterable<T>): Iterator<T> = chain(base.asSequence())

/**
 * Returns an iterator that goes over all the elements in the [Iterable]s of [base]
 */
fun <T> chain(base: Iterable<Iterable<T>>): Iterator<T> = chain(base.asSequence())