package processm.miners.heuristicminer

import org.junit.jupiter.api.*
import processm.core.comparators.CausalNetTraceComparison
import processm.core.helpers.allPermutations
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.*
import processm.core.verifiers.CausalNetVerifier
import processm.miners.heuristicminer.Helper.logFromModel
import processm.miners.heuristicminer.Helper.logFromString
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompareOfflineWithOnline {

    private fun seqs(model: Model) =
        CausalNetVerifier().verify(model)
            .validLoopFreeSequences
            .map { seq -> seq.map { ab -> ab.a }.filter { !it.special } }
            .toSet()

    private fun online(log: Log): Set<List<Node>> {
        val hm = OnlineHeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        hm.processLog(log)
        val onlineModel = hm.result
        return seqs(onlineModel)
    }

    private fun compare(log: Log, permuteLog: Boolean = false) {
        val hm = OfflineHeuristicMiner()
        hm.processLog(log)
        val offlineModel = hm.result
        val offlineSeqs = seqs(offlineModel)
        if (permuteLog) {
            for (perm in log.traces.toList().allPermutations()) {
                val onlineSeqs = online(Log(perm.asSequence()))
                assertEquals(offlineSeqs, onlineSeqs)
            }
        } else {
            val onlineSeqs = online(log)
            assertEquals(offlineSeqs, onlineSeqs)
        }
    }

    private fun compare(text: String, permuteLog: Boolean = false) =
        compare(logFromString(text), permuteLog = permuteLog)

    private fun compare(model: Model, permuteLog: (Log) -> Boolean = { false }) {
        val log = logFromModel(model)
        compare(log, permuteLog = permuteLog(log))
    }

    @Test
    fun `4 a b c d 4 a c b d`() {
        compare(
            """
             a b c d
             a b c d
             a b c d
             a b c d
             a c b d
             a c b d
             a c b d
             a c b d
        """.trimIndent()
        )
    }

    @Test
    fun `4 a b c d 2 a c b d`() {
        compare(
            """
             a b c d
             a b c d
             a b c d
             a b c d
             a c b d
             a c b d
        """.trimIndent()
        )
    }

    @Test
    fun `4 a b c d 1 a c b d`() {
        compare(
            """
             a b c d
             a b c d
             a b c d
             a b c d
             a c b d
        """.trimIndent()
        )
    }

    @Test
    fun `three parallel tasks`() {
        compare(
            """
             a b c d e
             a c b d e 
             a b d c e
             a c d b e
             a d b c e
             a d c b e
        """.trimIndent()
        )
    }

    @Test
    fun `two sequential diamonds`() {
        compare(
            """
                a b c d e f g
                a c b d e f g
                a b c d f e g
                a c b d f e g
            """.trimIndent(),
            true
        )
    }

    @Test
    fun `diamond`() {
        compare(
            """
                a b c d
                a c b d 
            """.trimIndent(),
            true
        )
    }

    @Test
    fun `flattened diamond`() {
        compare(
            """
                a b1 c1 b2 c2 d
                a b1 b2 c1 c2 d
                a b2 b1 c1 c2 d 
                a b1 b2 c2 c1 d
                a b2 b1 c2 c1 d
                a b2 c2 b1 c1 d
            """.trimIndent()
        )
    }

    @Test
    fun `diamond of diamonds`() {
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
        compare(causalnet {
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

    private fun helper(seed: Int, nNodes: Int): DynamicNode {
        val reference = RandomGenerator(Random(seed), nNodes = nNodes).generate()
        val log = logFromModel(reference)
        fun prepareOffline(): Pair<MutableModel, Boolean> {
            val hm = OfflineHeuristicMiner(
                bindingProvider = BestFirstBindingProvider(),
                longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
            )
            hm.processLog(log)
            val eq = CausalNetTraceComparison(reference, hm.result).equivalent
            return hm.result to eq
        }
        if (log.traces.count() <= 4) {
            val (offline, eq) = prepareOffline()
            return DynamicContainer.dynamicContainer("seed=$seed",
                log.traces.toList().allPermutations().mapIndexed { idx, traces ->
                    DynamicTest.dynamicTest("$idx") {
                        Assumptions.assumeTrue(eq)
                        val hm = OnlineHeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
                        hm.processLog(log)
                        val online = hm.result
                        assertTrue { CausalNetTraceComparison(online, offline).equivalent }
                    }
                })
        } else {
            return DynamicTest.dynamicTest("seed=$seed") {
                val (offline, eq) = prepareOffline()
                Assumptions.assumeTrue(eq)
                val hm = OnlineHeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
                hm.processLog(log)
                val online = hm.result
                assertTrue { CausalNetTraceComparison(online, offline).equivalent }
            }
        }
    }

    @TestFactory
    fun `nNodes=5`(): Iterator<DynamicNode> {
        return List(1000) { it }.asSequence().map { seed -> helper(seed, 5) }.iterator()
    }
}