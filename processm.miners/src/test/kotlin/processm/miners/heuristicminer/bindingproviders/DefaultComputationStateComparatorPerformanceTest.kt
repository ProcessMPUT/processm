package processm.miners.heuristicminer.bindingproviders

import io.mockk.every
import io.mockk.mockkConstructor
import processm.core.helpers.allPermutations
import processm.core.helpers.allSubsets
import processm.miners.heuristicminer.Helper.logFromString
import processm.miners.heuristicminer.OnlineHeuristicMiner
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals


class DefaultComputationStateComparatorPerformanceTest {

    private class ParametrizedDefaultComputationStateComparator(
        private val order: List<Int>,
        private val weights: List<Int>
    ) : Comparator<ComputationState> {
        init {
            require(order.size <= 4)
            require(weights.size == order.size)
        }

        private fun value(o: ComputationState): Array<Int> {
            val targets = o.trace.state.map { it.target }.toSet()
            val nMissing =
                (o.nodeTrace.subList(o.nextNode, o.nodeTrace.size).toSet() - targets).size
            val nTargets = targets.size
            val values = listOf(nMissing, nTargets, o.nextNode, o.trace.state.size)
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

    val logs = listOf(
        """
                a b1 c1 b2 c2 d
                a b1 b2 c1 c2 d
                a b2 b1 c1 c2 d
                a b1 b2 c2 c1 d
                a b2 b1 c2 c1 d
                a b2 c2 b1 c1 d
            """.trimIndent(),
        """
                a b c d e f g
                a c b d e f g
                a b c d f e g
                a c b d f e g
            """.trimIndent(),
        """
                 a b c d e
                 a c b d e
                 a b d c e
                 a c d b e
                 a d b c e
                 a d c b e
            """.trimIndent()
    ).map { logFromString(it) }

    /**
     * Whether to perform grid-search also on weights. Increases computational cost many times.
     */
    val adjustWeights = false

    /**
     * This is not a real tests. Its purpose is to decide which combination of features leads to the least number of polls from the priority queue in [BestFirstBindingProvider]
     */
    @Ignore
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
            for (order in listOf(0, 1, 2, 3).allSubsets().filter { it.isNotEmpty() }.flatMap { it.allPermutations() }) {
                val availableWeights = if (adjustWeights) {
                    listOf(-1, 1).product(order.size)
                } else {
                    val baseWeights = listOf(-1, -1, 1, -1)
                    sequenceOf(order.map { baseWeights[it] })
                }
                for (weights in availableWeights) {
                    ctr = 0
                    val comp = ParametrizedDefaultComputationStateComparator(order, weights)
                    val hm = OnlineHeuristicMiner(bindingProvider = BestFirstBindingProvider(comp))
                    hm.processLog(log)
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

}