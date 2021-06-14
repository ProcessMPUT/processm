package processm.experimental.heuristicminer.bindingproviders

import io.mockk.every
import io.mockk.mockkConstructor
import processm.core.helpers.Counter
import processm.core.helpers.allPermutations
import processm.core.helpers.mapToSet
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.experimental.heuristicminer.Helper.logFromModel
import processm.experimental.heuristicminer.OfflineHeuristicMiner
import processm.experimental.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.experimental.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals


@InMemoryXESProcessing
class DefaultComputationStateComparatorPerformanceTest {

    private class ParametrizedDefaultComputationStateComparator(
        private val order: List<Int>,
        private val weights: List<Int>
    ) : Comparator<ComputationState> {
        init {
            require(weights.size == order.size)
        }

        private fun value(o: ComputationState): Array<Int> {
            val targets = Counter<Node>()
            for (n in o.trace.state)
                targets.inc(n.target)
            val nTargets = targets.keys.size
            for (i in o.nextNode until o.nodeTrace.size)
                targets.dec(o.nodeTrace[i])
            val nMissing = -targets.values.sum()
            val targets2 = o.trace.state.mapToSet { it.target }
            val nMissing2 =
                (o.nodeTrace.subList(o.nextNode, o.nodeTrace.size).toSet() - targets).size
            val values = intArrayOf(nMissing, nTargets, o.nextNode, o.trace.state.size, targets.values.sum(), nMissing2)
            return (order.map { values[it] } zip weights)
                .map { (v, w) -> v * w }
                .toTypedArray()
        }

        override fun compare(o1: ComputationState, o2: ComputationState): Int {
            for ((x, y) in value(o1) zip value(o2))
                if (x != y)
                    return y - x
            return 0
        }
    }

    private fun <T> List<T>.product(n: Int): Sequence<List<T>> {
        val idx = List(n) { 0 }.toTypedArray()
        var ctr = 0
        return sequence {
            while (true) {
                yield(idx.map { this@product[it] })
                ctr += 1
                for (i in 0 until n) {
                    if (idx[i] < this@product.size - 1) {
                        idx[i] += 1
                        break
                    } else {
                        idx[i] = 0
                        if (i == n - 1)
                            return@sequence
                    }
                }
            }
        }
    }

    @Test
    fun testProduct() {
        assertEquals(
            setOf(
                listOf(0, 0, 0),
                listOf(0, 0, 1),
                listOf(0, 1, 0),
                listOf(0, 1, 1),
                listOf(1, 0, 0),
                listOf(1, 0, 1),
                listOf(1, 1, 0),
                listOf(1, 1, 1)
            ),
            listOf(0, 1).product(3).toSet()
        )
    }

    @Test
    fun testProduct2() {
        assertEquals(
            setOf(
                listOf(0, 0),
                listOf(0, 1),
                listOf(0, 2),
                listOf(1, 0),
                listOf(1, 1),
                listOf(1, 2),
                listOf(2, 0),
                listOf(2, 1),
                listOf(2, 2)
            ),
            listOf(0, 1, 2).product(2).toSet()
        )
    }

    fun diamondOfDiamonds(): Log {
        val a = Node("a")
        val b1 = Node("b1")
        val c1 = Node("c1")
        val d1 = Node("d1")
        val e1 = Node("e1")
        val b2 = Node("b2")
        val c2 = Node("c2")
        val d2 = Node("d2")
        val e2 = Node("e2")
        val f = Node("f")
        return logFromModel(causalnet {
            start = a
            end = f
            a splits b1 + b2
            b1 splits c1 + d1
            b2 splits c2 + d2
            c1 splits e1
            d1 splits e1
            c2 splits e2
            d2 splits e2
            e1 splits f
            e2 splits f
            a joins b1
            a joins b2
            b1 joins c1
            b1 joins d1
            b2 joins c2
            b2 joins d2
            c1 + d1 join e1
            c2 + d2 join e2
            e1 + e2 join f
        })
    }

    val logs = loadLogs() + listOf(diamondOfDiamonds())

    /**
     * Whether to perform grid-search also on weights. Increases computational cost many times.
     */
    val adjustWeights = false

    val defaultWeights = listOf(-1, -1, 1, -1, -1, -1)

    //not testing all possible combinations, as this is very time consuming
//    val consideredOrders=listOf(0,1,2,3,4,5).allSubsets().filter { it.isNotEmpty() }.flatMap { it.allPermutations() }
    val consideredOrders = listOf(
        listOf(1, 3),
        listOf(1),
        listOf(1, 0),
        listOf(1, 2),
        listOf(1, 2, 3),
        listOf(1, 2, 0),
        listOf(1, 2, 4)
    ).asSequence().flatMap { it.allPermutations() }

    /**
     * This is not a real tests. Its purpose is to decide which combination of features leads to the least number of polls from the priority queue in [BestFirstBindingProvider]
     */
    @Ignore("This is not a real test")
    @Test
    fun `grid-search on order and weights of features`() {
        var ctr = 0
        mockkConstructor(PriorityQueue::class)
        every { anyConstructed<PriorityQueue<ComputationState>>().poll() } answers {
            ctr += 1
            this.callOriginal()
        }
        val history = HashMap<Pair<List<Int>, List<Int>>, MutableList<Int>>()
        for (log in logs) {
            for (order in consideredOrders) {
                val availableWeights = if (adjustWeights) {
                    listOf(-1, 1).product(order.size)
                } else {
                    sequenceOf(order.map { defaultWeights[it] })
                }
                for (weights in availableWeights) {
                    println("$order/$weights")
                    ctr = 0
                    val comp = ParametrizedDefaultComputationStateComparator(order, weights)
                    val hm = OfflineHeuristicMiner(
                        dependencyGraphProvider = BasicDependencyGraphProvider(1),
                        bindingProvider = BestFirstBindingProvider(comp),
                        longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
                    )
                    hm.processLog(log)
                    val model = hm.result   //perform lazy operations
                    history.getOrPut(order to weights, { ArrayList() }).add(ctr)
                }
            }
        }
        for ((k, v) in history.entries)
            println("$k -> $v")
        val averaged = history.mapValues { (_, v) -> v.average() }
        val bestValue = averaged.values.min()
        val best = averaged.filterValues { it == bestValue }.keys
        println("Best average: $bestValue")
        best.sortedBy { it.first.size }.forEach { println(it) }
    }

    private fun loadLogs() = listOf(
        "../xes-logs/activities_of_daily_living_of_several_individuals-edited_hh102_weekends.xes.gz",
        "../xes-logs/activities_of_daily_living_of_several_individuals-edited_hh110_weekends.xes.gz"
    ).map {
        File(it).inputStream().use { fileStream ->
            GZIPInputStream(fileStream).use { stream ->
                HoneyBadgerHierarchicalXESInputStream(
                    XMLXESInputStream(
                        stream
                    )
                ).single()
            }
        }
    }
}