package processm.core.helpers

/**
 * A trie (prefix tree) mapping a sequence of keys of the type [K] to a value of the type [V]
 * Conversely to the usual approach to tries, the keys are labels on edges, not on nodes.
 * The values are stored in nodes.
 *
 * The default value for each prefix is given by the [initializer] function.
 * To retrieve a node corresponding to a given prefix, one must call [get] for each element of the prefix in order, e.g.,
 * ```
 * var node = root
 * prefix.forEach { node = node[it] }
 * ```
 * (Or use a short-hand [get] for [Iterable])
 *
 * Use [update] to change the value associated with the current node
 *
 * Finally, to retrieve all prefixes stored in the trie with their corresponding values and the outgoing edges,
 * iterate over the trie. The prefixes are returned in the DFS order. Be careful: for efficiency, [Entry.prefix] is
 * shared by all elements returned by an iterator, and thus must be copied manually if it is to be accessed after
 * generating the next element.
 */
class TrieCounter<K, V>(private val initializer: () -> V) : Sequence<TrieCounter.Entry<K, TrieCounter<K, V>>> {

    //TODO expose children as a read-only map, get rid of the third argument of Entry, rename value to trie or sth
    data class Entry<K, V>(val prefix: List<K>, val value: V, val children: Set<K>)

    var value: V = initializer()
        private set
    private var children: HashMap<K, TrieCounter<K, V>>? = null

    fun getOrNull(key: K): TrieCounter<K, V>? = children?.get(key)

    /**
     * Returns a [TrieCounter] corresponding to the child [key], creating and storing a new instance of [TrieCounter] if necessary
     */
    operator fun get(key: K): TrieCounter<K, V> {
        if (children == null)
            children = HashMap()
        return children!!.computeIfAbsent(key) { TrieCounter(initializer) }
    }

    /**
     * A shorthand for retrieving a [TrieCounter] corresponding to the final element of [prefix], by traversing the trie according to the [prefix]
     */
    operator fun get(prefix: Iterable<K>): TrieCounter<K, V> {
        var node = this
        prefix.forEach { node = node[it] }
        return node
    }

    fun update(updateFunction: (V) -> V) {
        value = updateFunction(value)
    }

    private fun flatten(prefix: ArrayList<K>): Sequence<Entry<K, TrieCounter<K, V>>> = sequence {
        val children = this@TrieCounter.children
        yield(Entry(prefix, this@TrieCounter, children?.keys.orEmpty()))
        if (children !== null) {
            for ((child, subtrie) in children) {
                prefix.add(child)
                yieldAll(subtrie.flatten(prefix))
                prefix.removeAt(prefix.size - 1)
            }
        }
    }

    override fun iterator(): Iterator<Entry<K, TrieCounter<K, V>>> = flatten(ArrayList()).iterator()

    fun clear() {
        value = initializer()
        children = null
    }

    /**
     * Returns true if, and only if, [prefix] is empty or if there is a path starting in this node labeled with the values given in [prefix]
     */
    operator fun contains(prefix: List<K>): Boolean {
        val c = children
        if (prefix.isEmpty())
            return true
        if (c !== null) {
            val child = c[prefix[0]]
            return child != null && prefix.subList(1, prefix.size) in child
        }
        return false
    }

    /**
     * Returns true if the current node contains an edge labeled [key]
     */
    operator fun contains(key: K): Boolean {
        val c = children
        return c !== null && key in c
    }
}