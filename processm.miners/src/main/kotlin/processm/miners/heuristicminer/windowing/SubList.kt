package processm.miners.heuristicminer.windowing

/**
 * Really, there should be some public implementation of this in some library.
 */
internal class SubList<T>(val base:List<T>, val fromIndex: Int, val toIndex: Int):List<T> {
    override val size: Int
        get() = toIndex-fromIndex

    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): T = base[index+fromIndex]

    override fun indexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<T> = object:Iterator<T> {
        var idx=fromIndex

        override fun hasNext(): Boolean = idx < toIndex

        override fun next(): T = base[idx++]

    }

    override fun lastIndexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun listIterator(): ListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> =
        SubList(
            this,
            fromIndex,
            toIndex
        )

}