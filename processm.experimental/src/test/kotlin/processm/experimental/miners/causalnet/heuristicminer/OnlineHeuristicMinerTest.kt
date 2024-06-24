package processm.experimental.miners.causalnet.heuristicminer

import processm.core.log.Helpers.event
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.experimental.miners.causalnet.heuristicminer.traceregisters.CompleteTraceRegister
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostParsimoniousHypothesisSelector
import processm.miners.causalnet.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import kotlin.test.Test
import kotlin.test.assertEquals

class OnlineHeuristicMinerTest {

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
        val hm = OnlineHeuristicMiner(bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()))
        hm.processLog(log)
        with(hm.result) {
            assertEquals(setOf(Split(setOf(Dependency(a, b))), Split(setOf(Dependency(a, end)))), splits[a]?.toSet())
            assertEquals(setOf(Split(setOf(Dependency(b, c))), Split(setOf(Dependency(b, end)))), splits[b]?.toSet())
            assertEquals(setOf(Split(setOf(Dependency(c, end)))), splits[c]?.toSet())
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
        val hm = OnlineHeuristicMiner(bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()))
        hm.processLog(log)
        with(hm.result) {
            assertEquals(joins[a], listOf(Join(setOf(Dependency(start, a)))))
            assertEquals(joins[b], listOf(Join(setOf(Dependency(start, b))), Join(setOf(Dependency(a, b)))))
            assertEquals(joins[c], listOf(Join(setOf(Dependency(start, c))), Join(setOf(Dependency(b, c)))))
        }
    }

    @Test
    fun diamond() {
        val log= logFromString(   """
                a b c d
                a c b d 
            """.trimIndent())
        val hm = OnlineHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
    }

    @Test
    fun diamond2() {
        val log= logFromString(   """
                a b c d
                a c b d 
            """.trimIndent()).traces.toList()
        val hm = OnlineHeuristicMiner(
            traceRegister = CompleteTraceRegister(),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        hm.processTrace(log[0])
        println(hm.result)
        hm.unprocessTrace(log[0])
        hm.processTrace(log[1])
        println(hm.result)
    }
}
