package processm.experimental.performance

class Trie<T>(
    val node: T?,
    val prefix: List<T>,
    private val _children: MutableMap<T?, Trie<T>>
):Sequence<List<T>> {

    val children:Map<T?, Trie<T>>
        get() = _children

    override fun iterator(): Iterator<List<T>> =flatten().iterator()

    private fun flatten(): Sequence<List<T>> = sequence {
        if (node == null)
            yield(prefix)
        else
            for (child in children.values)
                yieldAll(child.flatten())
    }

    private fun containsAnyCorooted(needle:Trie<T>): Boolean {
        if(this.node != needle.node)
            return false
        if(this.node == null) {
            assert(needle.node == null)
            return true
        }
        return needle.children.any { (a, trie) -> this.children[a]?.containsAnyCorooted(trie)?:false }
    }

    fun containsAny(needle: Trie<T>): Boolean {
        if(this.prefix.size > needle.prefix.size)
            return needle.containsAny(this)
        var n=this
        if(this.prefix.size < needle.prefix.size) {
            if(this.prefix != needle.prefix.subList(0, this.prefix.size))
                return false
            for(item in needle.prefix.subList(this.prefix.size, needle.prefix.size))
                n = n.children[item]?:return false
        }
        assert(n.prefix.size == needle.prefix.size)
        return n.prefix == needle.prefix && n.containsAnyCorooted(needle)
    }

    operator fun contains(needle: List<T>): Boolean {
        if(this.node != needle[0])
            return false
        var n = this
        for(pos in 1 until needle.size) {
            n = n.children[needle[pos]]?:return false
        }
        return null in n.children
    }

    fun dump(indent: String = "") {
        println("$indent$node")
        for (child in children.values)
            child.dump("$indent  ")
    }

    fun isEmpty(): Boolean = children.isEmpty() && node != null

    fun isNotEmpty(): Boolean = !isEmpty()

    fun remove(list:List<T>):Boolean {
        val tries = arrayListOf(this)
        var current = this
        if(this.node != list[0])
            return false
        for(pos in 1 until list.size) {
            assert(current.node == list[pos-1])
            val tmp = current.children[list[pos]] ?: return false
            current = tmp
            tries.add(current)
        }
        current = current.children[null] ?: return false
        for(prev in tries.reversed()) {
            prev._children.remove(current.node)
            if(prev._children.isNotEmpty())
                break
            current = prev
        }
        return true
    }

    val size: Int
        get() = flatten().count()

    companion object {
        fun<T> build(items: Collection<List<T>>): Trie<T> {
            val tries = build(emptyList(), items, 0)
            return tries.values.single()
        }

        private fun<T> build(
            label: List<T>,
            items: Collection<List<T>>,
            pos: Int
        ): MutableMap<T?, Trie<T>> =
            items
                .groupBy { if (pos < it.size) it[pos] else null }
                .mapValues { (activity, relevantPrefixes) ->
                    if (activity != null) {
                        val newLabel = label + listOf(activity)
                        Trie<T>(activity, newLabel, build(newLabel, relevantPrefixes, pos + 1))
                    } else
                        Trie<T>(activity, label, HashMap())
                }
                .toMutableMap()
    }
}