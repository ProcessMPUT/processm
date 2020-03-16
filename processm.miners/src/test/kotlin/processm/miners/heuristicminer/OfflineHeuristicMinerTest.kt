package processm.miners.heuristicminer

import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.miners.heuristicminer.hypothesisselector.MostParsimoniousHypothesisSelector
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineHeuristicMinerTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")

    @Test
    fun `a or ab or abc`() {
        val log = Log(listOf(
            "a" to 1,
            "ab" to 1,
            "abc" to 1
        ).asSequence()
            .flatMap { (s, n) -> List(n) { Trace(s.map { e -> event(e.toString()) }.asSequence()) }.asSequence() })
        val hm = OfflineHeuristicMiner(log, hypothesisSelector = MostParsimoniousHypothesisSelector())
        with(hm.result) {
            assertEquals(splits[a], setOf(Split(setOf(Dependency(a, b))), Split(setOf(Dependency(a, end)))))
            assertEquals(splits[b], setOf(Split(setOf(Dependency(b, c))), Split(setOf(Dependency(b, end)))))
            assertEquals(splits[c], setOf(Split(setOf(Dependency(c, end)))))
        }
    }

    @Test
    fun `c or bc or abc`() {
        val log = Log(listOf(
            "c" to 1,
            "bc" to 1,
            "abc" to 1
        ).asSequence()
            .flatMap { (s, n) -> List(n) { Trace(s.map { e -> event(e.toString()) }.asSequence()) }.asSequence() })
        val hm = OfflineHeuristicMiner(log, hypothesisSelector = MostParsimoniousHypothesisSelector())
        with(hm.result) {
            assertEquals(joins[a], setOf(Join(setOf(Dependency(start, a)))))
            assertEquals(joins[b], setOf(Join(setOf(Dependency(start, b))), Join(setOf(Dependency(a, b)))))
            assertEquals(joins[c], setOf(Join(setOf(Dependency(start, c))), Join(setOf(Dependency(b, c)))))
        }
    }

    @Test
    fun `all subsets`() {
        assertEquals(
            setOf(
                setOf(),
                setOf("a"), setOf("b"), setOf("c"),
                setOf("a", "b"), setOf("a", "c"), setOf("b", "c"),
                setOf("a", "b", "c")
            ), setOf("a", "b", "c").allSubsets().toSet()
        )
    }
}