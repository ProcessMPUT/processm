package processm.experimental.performance

import processm.core.log.Event
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNetState
import processm.core.models.commons.Activity

class AlignmentStep(val event: Event?, val activity: Activity?, val stateBefore: CausalNetState)

data class AlignmentContext(val model: CausalNet, val events: List<Event>, val distance: Distance)

data class AlignmentState(val state: CausalNetState, val tracePosition: Int)


private class LazyList<T>(override val size: Int, private val create: (Int) -> T) : List<T> {
    private val backend: MutableList<T?> = MutableList(size) { null }
    override fun contains(element: T): Boolean = indexOf(element) >= 0

    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    override fun get(index: Int): T {
        var v = backend[index]
        if (v == null) {
            v = create(index)
            backend[index] = v
        }
        return v!!
    }

    override fun indexOf(element: T): Int {
        for (i in 0..size)
            if (get(i) == element)
                return i
        return -1
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var i = 0
        override fun hasNext(): Boolean = i < size

        override fun next(): T = get(i++)
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

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        TODO("Not yet implemented")
    }
}

class Alignment(
    val cost: Double,
    val alignment: Iterable<AlignmentStep>,
    internal val state: CausalNetState,
    internal val tracePosition: Int,
    internal val context: AlignmentContext
) {
    internal val features: List<Lazy<Double>>

    internal val alignmentState: AlignmentState
        get() = AlignmentState(state, tracePosition)

    /**
     * Cost must go first, otherwise the algorithm ceases to yield optimal alignments.
     */
    init {

        /*
        val nearest = if(localEvents!=null && a.tracePosition < localEvents.size) {
            val event = localEvents[a.tracePosition]
            model.available(a.state).map { dec -> distance(dec.activity, event) }.min()
                ?: Double.POSITIVE_INFINITY
        } else
            Double.POSITIVE_INFINITY

         */
//        features = listOf(cost, -tracePosition.toDouble(), -matching.toDouble())
        features = listOf(
            lazy {cost},
            lazy {-tracePosition.toDouble()},
            lazy {
                val matching =
                    if (tracePosition < context.events.size) {
                        val availableActivities = context.model.availableNodes(state).toList()
                        var ctr = 0
                        for (i in tracePosition until context.events.size) {
                            val e = context.events[i]
                            if (availableActivities.any { a -> context.distance(a, e) == 0.0 })
                                ctr++
                        }
                        ctr
                    } else 0
                -matching.toDouble()}
        )
        /*
        features = LazyList(3) { idx ->
            return@LazyList if (idx == 0) cost
            else if (idx == 1) -tracePosition.toDouble()
            else {

                -matching.toDouble()
            }
        }
         */
    }
}