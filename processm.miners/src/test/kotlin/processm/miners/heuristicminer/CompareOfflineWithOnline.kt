package processm.miners.heuristicminer

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import processm.core.comparators.CausalNetTraceComparison
import processm.core.helpers.allPermutations
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.RandomGenerator
import processm.core.models.causalnet.causalnet
import processm.core.verifiers.CausalNetVerifier
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import javax.management.DynamicMBean
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
        val hm = HeuristicMiner()
        hm.processLog(log)
        val onlineModel = hm.result
        return seqs(onlineModel)
    }

    private fun compare(log: Log, permuteLog: Boolean = false) {
        val offlineModel = OfflineHeuristicMiner(log).result
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

    private fun logFromString(text: String): Log =
        Log(
            text.split('\n')
                .map { line -> Trace(line.split(" ").filter { it.isNotEmpty() }.map { event(it) }.asSequence()) }
                .asSequence()
        )

    private fun logFromModel(model: Model): Log {
        val tmp = CausalNetVerifier().verify(model).validLoopFreeSequences
            .toList()
        return Log(tmp.map { seq -> Trace(seq.asSequence().map { ab -> event(ab.a.activity) }) }.asSequence())
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

    @Ignore
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

    private fun helper(seed: Int, nNodes:Int): DynamicNode? {
        val reference = RandomGenerator(Random(seed), nNodes = nNodes).generate()
        val log = logFromModel(reference)
        val offline = OfflineHeuristicMiner(
            log,
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        ).result
        if (CausalNetTraceComparison(reference, offline).equivalent) {
            if (log.traces.count() <= 4) {
                return DynamicContainer.dynamicContainer("seed=$seed",
                    log.traces.toList().allPermutations().mapIndexed { idx, traces ->
                        DynamicTest.dynamicTest("$idx") {
                            val hm = HeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
                            hm.processLog(log)
                            val online = hm.result
                            assertTrue { CausalNetTraceComparison(online, offline).equivalent }
                        }
                    })
            } else {
                return DynamicTest.dynamicTest("seed=$seed") {
                    val hm = HeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
                    hm.processLog(log)
                    val online = hm.result
                    assertTrue { CausalNetTraceComparison(online, offline).equivalent }
                }
            }
        } else {
            return null
        }
    }

    @TestFactory
    fun `nNodes=5`(): Iterator<DynamicNode> {
        return List(1000) { it }.asSequence().map { seed -> helper(seed, 5) }.filterNotNull().iterator()
    }

//    @Ignore("Dunno, my IntelliJ behaves strangely with this enabled")
//    @TestFactory
//    fun `nNodes=7`(): Iterator<DynamicNode> {
//        return List(100) { it }.asSequence().map { seed -> helper(seed, 7) }.filterNotNull().iterator()
//    }
//
//    @TestFactory
//    fun `nNodes=9`(): Iterator<DynamicNode> {
//        return List(100) { it }.asSequence().map { seed -> helper(seed, 9) }.filterNotNull().iterator()
//    }

    @Test
    fun `tmp`() {
        val random = RandomGenerator(Random(53), nNodes = 5).generate()
        val log = logFromModel(random)
        val offline = OfflineHeuristicMiner(
            log,
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        ).result
        val hm = HeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        hm.processLog(log)
        val online = hm.result
        val cmp1=CausalNetTraceComparison(random, offline)
        val cmp2=CausalNetTraceComparison(random, online)
        val cmp3=CausalNetTraceComparison(online, offline)
        println("RANDOM")
        println(cmp1.leftTraces)
        println(random)
        println("OFFLINE")
        println(cmp1.rightTraces)
        println(offline)
        println("ONLINE")
        println(cmp3.leftTraces)
        println(online)
        println("RANDOM == OFFLINE ${cmp1.equivalent}")
        println("RANDOM == ONLINE ${cmp2.equivalent}")
        println("OFFLINE SOUND ${cmp1.rightVerficiationReport.isSound}")
        println("ONLINE == OFFLINE ${cmp3.equivalent}")
        assertTrue { CausalNetTraceComparison(online, offline).equivalent }
    }
}