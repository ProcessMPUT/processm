package processm.core.helpers

private class PairedCollection<T>(val backingCollection: List<T>) : Collection<Pair<T, T>> {
    override val size: Int
        get() = backingCollection.size * (backingCollection.size - 1) / 2

    override fun contains(pair: Pair<T, T>): Boolean =
        pair.first in backingCollection && pair.second in backingCollection

    override fun containsAll(pairs: Collection<Pair<T, T>>): Boolean {
        for (pair in pairs) {
            if (pair !in this)
                return false
        }
        return true
    }

    override fun isEmpty(): Boolean = backingCollection.size <= 1

    override fun iterator(): Iterator<Pair<T, T>> = iterator {
        for ((i, e) in backingCollection.withIndex()) {
            val iterator = backingCollection.listIterator(i + 1)
            while (iterator.hasNext())
                yield(Pair(e, iterator.next()))
        }
    }
}

/**
 * Lazily calculates all pairs of the items in this list. The returned collection is view on this list
 * and all changes to this list are immediately reflected in the returned collection.
 */
fun <T> List<T>.allPairs(): Collection<Pair<T, T>> =
    PairedCollection(this)
