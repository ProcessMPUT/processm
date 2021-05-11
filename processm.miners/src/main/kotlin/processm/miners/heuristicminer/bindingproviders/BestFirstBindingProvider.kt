package processm.miners.heuristicminer.bindingproviders

import com.google.common.collect.MinMaxPriorityQueue
import org.apache.commons.collections4.MultiSet
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.mapToSet
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.core.models.commons.ProcessModelState
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.ReplayTrace
import java.lang.ref.SoftReference
import java.util.*

/**
 * Represents a node in a search graph of [BestFirstBindingProvider]
 *
 * @param nextNode Index of the node in [nodeTrace] to be considered next
 * @param trace States, joins and splits so far
 * @param nodeTrace Complete trace being considered
 */
data class ComputationState(val nextNode: Int, val trace: ReplayTrace, val nodeTrace: NodeTrace) {

    /**
     * Priority in a priority queue. The following is considered:
     * 1. Minimal number of nodes that are yet to be visited according to the current state (fewer is better).
     * 2. Position in trace to be considered next (higher is better).
     *
     * The corresponding experimental evaluation is in [DefaultComputationStateComparatorPerformanceTest]
     */
    val value: IntArray by lazy {
        val nTargets = trace.state.uniqueSet().size
        return@lazy intArrayOf(-nTargets, nextNode)
    }
}

class DefaultComputationStateComparator : Comparator<ComputationState> {
    override fun compare(o1: ComputationState, o2: ComputationState): Int {
        for ((x, y) in o1.value zip o2.value)
            if (x != y)
                return y - x
        return 0
    }
}

class LazyCausalNetState(
    private val base: CausalNetState,
    private val consume: Collection<Dependency>,
    private val produce: Collection<Dependency>
) : CausalNetState {

    private var backendImpl = SoftReference<CausalNetStateImpl>(null)

    private val backend: CausalNetStateImpl
        get() {
            var tmp = backendImpl.get()
            if (tmp == null) {
                tmp = doMaterialize(base)
                backendImpl = SoftReference(tmp)
            }
            return tmp
        }

    private fun doMaterialize(base: CausalNetState): CausalNetStateImpl {
        val tmp = CausalNetStateImpl(base)
        for (c in consume)
            tmp.remove(c)
        tmp.addAll(produce)
        return tmp
    }

    override fun hashCode(): Int = backend.hashCode()

    override fun equals(other: Any?): Boolean = Objects.equals(backend, other)

    override fun contains(element: Dependency?): Boolean = backend.contains(element)

    override fun addAll(elements: Collection<Dependency>): Boolean = backend.addAll(elements)

    override fun clear() = backend.clear()

    override fun removeAll(elements: Collection<Dependency>): Boolean = backend.removeAll(elements)

    override fun add(element: Dependency?): Boolean = backend.add(element)

    override fun add(`object`: Dependency?, occurrences: Int): Int = backend.add(`object`, occurrences)

    override fun iterator(): MutableIterator<Dependency> = backend.iterator()

    override fun setCount(`object`: Dependency?, count: Int): Int = backend.setCount(`object`, count)

    override fun entrySet(): MutableSet<MultiSet.Entry<Dependency>> = backend.entrySet()

    override fun getCount(`object`: Any?): Int = backend.getCount(`object`)

    override fun uniqueSet(): MutableSet<Dependency> = backend.uniqueSet()

    override fun isEmpty(): Boolean = backend.isEmpty()

    override fun remove(`object`: Any?, occurrences: Int): Int = backend.remove(`object`, occurrences)

    override fun remove(element: Dependency?): Boolean = backend.remove(element)

    override fun containsAll(elements: Collection<Dependency>): Boolean = backend.containsAll(elements)

    override fun retainAll(elements: Collection<Dependency>): Boolean = backend.retainAll(elements)

    override val size: Int
        get() = backend.size

    override fun copy(): ProcessModelState {
        TODO("Not yet implemented")
    }
}

/**
 * Computes bindings for a given trace in a give model using best-first principle, where best is defined by [comparator].
 *
 * [BestFirstBindingProvider] maintains a priority queue of partial [ReplayTrace]s and, at each step, considers the best
 * currently available. Once it reaches any correct and complete replay trace, it immediately stops computation and returns it.
 */
class BestFirstBindingProvider(
    val comparator: Comparator<ComputationState> = DefaultComputationStateComparator(),
    val maxQueueSize: Int = 1000000
) :
    AbstractBindingProvider() {

    companion object {
        private val logger = logger()
    }

    private fun maxSetSize(n: Int): Int {
        var s = 0.0
        var p = 1.0
        for (k in 1..n) {
            p *= (n - (k - 1)).toDouble() / k
            s += p
            if (s > maxQueueSize)
                return k
        }
        return n
    }

    override fun computeBindings(model: CausalNet, trace: List<Node>): List<Binding> {
        //val queue = PriorityQueue(comparator)
        val queue = MinMaxPriorityQueue.orderedBy(comparator).maximumSize(maxQueueSize).create<ComputationState>()
        queue.add(ComputationState(0, ReplayTrace(CausalNetStateImpl(), listOf(), listOf()), trace))

//        val visited = HashSet<Pair<Int, CausalNetStateImpl>>()
        val available = HashSet<Node>()
        val produceCandidates: MutableList<Iterable<Collection<Dependency>>> =
            MutableList(trace.size) { emptyList<Collection<Dependency>>() }
        val availableAt = MutableList(trace.size) { emptySet<Node>() }
        for (idx in trace.indices.reversed()) {
            val currentNode = trace[idx]
            val size = maxSetSize(available.size)
            //logger.debug { "idx=$idx #available=${available.size} maxQueueSize=$maxQueueSize maxSetSize=$size" }
            produceCandidates[idx] = produceCandidates(model, currentNode, available, size)
            //logger.debug { "${produceCandidates[idx].count()}" }
            availableAt[idx] = HashSet(available)
            available.add(currentNode)
        }
//        check(trace.size<=11 || produceCandidates[11].count()>0)
//        var printCtr = 0
//        var skipCtr = 0
        while (queue.isNotEmpty()) {
//            printCtr++
//            if (printCtr == 100) {
//                logger.debug { "queue size ${queue.size}" }
//                printCtr = 0
//            }
            val compState = queue.poll()!!
            val (currentNodeIdx, traceSoFar) = compState
            val copy = CausalNetStateImpl(traceSoFar.state)
            if (currentNodeIdx == trace.size) {
                if (copy.isEmpty()) {
                    val (_, joins, splits) = traceSoFar
                    val finalSplits = splits.filter { split -> split.isNotEmpty() }
                        .map { split -> Split(split.mapToSet { (a, b) -> Dependency(a, b) }) }
                    val finalJoins = joins.filter { join -> join.isNotEmpty() }
                        .map { join -> Join(join.mapToSet { (a, b) -> Dependency(a, b) }) }
//                    logger.debug("skipCtr=$skipCtr")
                    return finalSplits + finalJoins
                }
                continue
            }
//            val key = currentNodeIdx to copy
//            if (visited.contains(key)) {
//                //skipCtr++
//                continue
//            }
//            visited.add(key)
            val avail = copy.uniqueSet()
//            val alreadyProduced = avail.mapToSet { it.target }
//            val mustProduceNodes = availableAt[currentNodeIdx].intersect(alreadyProduced)
            val currentNode = trace[currentNodeIdx]
//            val mustProduce = model.outgoing[currentNode].orEmpty().filter { it.target in mustProduceNodes }
            //           val availableForProduction = availableAt[currentNodeIdx] //- (alreadyProduced-currentNode)
//            logger.debug {"available ${availableAt[currentNodeIdx]} produced ${alreadyProduced} for production ${availableForProduction} must produce $mustProduce"}
            //logger.debug("currentNodeIdx=$currentNodeIdx")
            //logger.debug("Max consume size: $size, avail.size=${avail.size} produce ${produceCandidates[currentNodeIdx].count()}")
            for (consume in consumeCandidates(model, currentNode, avail)) {
                if (copy.containsAll(consume)) {
                    for (produce in produceCandidates[currentNodeIdx]) {
                        val it = ReplayTrace(
                            LazyCausalNetState(traceSoFar.state, consume, produce),
                            HierarchicalIterable(traceSoFar.joins, consume),
                            HierarchicalIterable(traceSoFar.splits, produce)
                        )
                        queue.add(ComputationState(currentNodeIdx + 1, it, trace))
                    }
                }
            }
        }
        logger.warn("Failed to compute bindings for $trace")
//        logger.warn(model.toString())
        return emptyList()
    }
}