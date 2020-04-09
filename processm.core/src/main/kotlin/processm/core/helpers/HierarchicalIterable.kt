package processm.core.helpers

import java.util.*


/**
 * An read-only view to a collection consisting of [prefix] immediately followed by [suffix] (i.e., `prefix+suffix`).
 * It aims to save memory by sharing [prefix] and/or [suffix] with other collections.
 *
 * By depth of a list we understand number of prefixes one must visit before arriving at a prefix that is not a [HierarchicalIterable]
 *
 * @param maxDepth If list is at least this deep, we flatten it before allowing to iterate. This is to avoid [StackOverflowError].
 * The flattened version is cached, and thus the iterable is decoupled from the original data.
 */
class HierarchicalIterable<T>(val prefix: Iterable<T>, val suffix: T, val maxDepth: Int = 100) : Iterable<T> {

    val depth: Int = if (prefix is HierarchicalIterable) prefix.depth + 1 else 0
    private var flat: List<T>? = null

    override fun iterator(): Iterator<T> {
        if (depth < maxDepth) {
            return object : Iterator<T> {
                private val i = prefix.iterator()
                private var suffixUsed = false
                override fun hasNext(): Boolean = i.hasNext() || !suffixUsed

                override fun next(): T = if (i.hasNext()) i.next() else suffix.also { suffixUsed = true }
            }
        } else {
            val i = flat?.iterator()
            if (i != null)
                return i
            //we may be prone to StackOverflow here, so we flatten instead
            val result = ArrayList<T>()
            var j = this
            while (true) {
                result.add(j.suffix)
                val p = j.prefix
                if (p is HierarchicalIterable && p.flat == null)
                    j = p
                else {
                    result.reverse()
                    val tmp = if (p is HierarchicalIterable) p.flat else p
                    check(tmp != null)
                    flat = tmp + result
                    break
                }
            }
            return flat!!.iterator()
        }
    }

}
