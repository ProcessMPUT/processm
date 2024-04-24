package processm.helpers

/**
 * Creates a set made of the contents of the given [iterable]s. The resulting collection keeps only the first copy
 * of the duplicate items.
 */
fun <R> flatSetOf(vararg iterable: Iterable<R>): Set<R> {
    val flatSet = HashSet<R>()
    iterable.forEach {
        flatSet += it
    }
    return flatSet
}

