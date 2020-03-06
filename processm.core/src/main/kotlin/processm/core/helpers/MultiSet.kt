package processm.core.helpers

open class MultiSet<E>() : MutableCollection<E> {

    protected val data = HashMap<E, Int>()

    private constructor(elements: Collection<E>) : this() {
        addAll(elements)
    }

    constructor(other: MultiSet<E>) : this() {
        data.putAll(other.data)
    }

    override val size: Int
        get() = data.values.sum()

    override fun contains(element: E): Boolean {
        return data.containsKey(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return MultiSet(elements).data.all { (k, v) -> v <= data.getOrDefault(k, 0) }
    }

    override fun isEmpty(): Boolean {
        return data.isEmpty()
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {
            private val base = data.iterator()
            private var ctr = 0
            private var current: E? = null

            private fun goToNext() {
                if (ctr == 0 || current == null) {
                    if (base.hasNext()) {
                        val tmp = base.next()
                        current = tmp.key
                        ctr = tmp.value
                    } else {
                        ctr = 0
                        current = null
                    }
                }
            }

            override fun hasNext(): Boolean {
                goToNext()
                return ctr > 0
            }

            override fun next(): E {
                goToNext()
                if (current != null) {
                    ctr -= 1
                    return current as E
                } else
                    throw NoSuchElementException()
            }

            override fun remove() {
                TODO("not implemented")
            }

        }
    }

    override fun add(element: E): Boolean {
        data[element] = data.getOrDefault(element, 0) + 1
        return true
    }

    override fun addAll(elements: Collection<E>): Boolean {
        elements.forEach { add(it) }
        return true
    }

    override fun clear() {
        data.clear()
    }

    override fun remove(element: E): Boolean {
        if (data.containsKey(element)) {
            val n = data.getValue(element)
            if (n > 1)
                data[element] = n - 1
            else
                data.remove(element)
            return true
        }
        return false
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        var result = false
        elements.forEach {
            if (remove(it))
                result = true
        }
        return result
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        TODO("I'm not sure about the semantics we are aiming for here, does {1,1}.retainAll({1})={1} or {1,1}.retainAll({1})={1,1}?")
    }

    override fun equals(other: Any?): Boolean {
        if (other != null && other is MultiSet<*>) {
            return data == other.data
        }
        return false
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

}