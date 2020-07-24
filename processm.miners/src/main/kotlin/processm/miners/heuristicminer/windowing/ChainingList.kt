package processm.miners.heuristicminer.windowing

internal class ChainingList<T>(val left:List<T>, val middle:T, val right:List<T>):List<T> {
    override val size: Int
        get() = left.size + right.size + 1

    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): T =
        when {
            index < left.size -> left[index]
            index == left.size -> middle
            else -> right[index - (left.size+1)]
        }

    override fun indexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<T> = object:Iterator<T> {
        val l=left.iterator()
        val r=right.iterator()
        var m=true

        override fun hasNext(): Boolean = l.hasNext() || m || r.hasNext()

        override fun next(): T =
            when {
                l.hasNext() -> l.next()
                m -> {
                    m=false
                    middle
                }
                else -> r.next()
            }
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