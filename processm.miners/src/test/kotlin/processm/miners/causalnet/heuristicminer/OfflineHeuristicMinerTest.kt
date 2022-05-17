package processm.miners.causalnet.heuristicminer

import org.junit.jupiter.api.Assumptions
import processm.core.comparators.CausalNetTraceComparison
import processm.core.helpers.mapToSet
import processm.core.log.Helpers.event
import processm.core.log.Helpers.logFromModel
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.core.verifiers.CausalNetVerifier
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostParsimoniousHypothesisSelector
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.L2DependencyGraphProvider
import processm.miners.causalnet.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineHeuristicMinerTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
    val f = Node("f")
    val g = Node("g")

    @Test
    fun `a or ab or abc`() {
        val log = Log(listOf(
            "a" to 1,
            "ab" to 1,
            "abc" to 1
        ).asSequence()
            .flatMap { (s, n) -> List(n) { Trace(s.map { e -> event(e.toString()) }.asSequence()) }.asSequence() })
        val hm = OfflineHeuristicMiner(bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()))
        hm.processLog(log)
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
        val hm = OfflineHeuristicMiner(bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()))
        hm.processLog(log)
        with(hm.result) {
            assertEquals(joins[a], setOf(Join(setOf(Dependency(start, a)))))
            assertEquals(joins[b], setOf(Join(setOf(Dependency(start, b))), Join(setOf(Dependency(a, b)))))
            assertEquals(joins[c], setOf(Join(setOf(Dependency(start, c))), Join(setOf(Dependency(b, c)))))
        }
    }

    @Test
    fun `l2 loop`() {
        val log = Log(listOf(
            "abcd" to 1,
            "abcbcd" to 1,
            "abcbcbcd" to 1,
            "abcbcbcbcd" to 1
        ).asSequence()
            .flatMap { (s, n) -> List(n) { Trace(s.map { e -> event(e.toString()) }.asSequence()) }.asSequence() })
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = L2DependencyGraphProvider(1, 1e-5, 1e-5),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector())
        )
        hm.processLog(log)
        with(hm.result) {
            assertEquals(setOf(Split(setOf(Dependency(c, b))), Split(setOf(Dependency(c, d)))), splits[c])
            assertEquals(setOf(Split(setOf(Dependency(b, c)))), splits[b])
        }
    }

    @Ignore("Known to fail. HM is unable to recreate the model under both MostGreedyHypothesisSelector and MostParsimoniousHypothesisSelector")
    @Test
    fun `try to recreate`() {
        val reference = causalnet {
            start = a
            end = g
            a splits d or e or f
            d splits e + f
            e splits f or g
            f splits g
            a joins d
            a or d join e
            a or d or d + e or e join f
            e or f or e + f join g
        }
        val v = CausalNetVerifier().verify(reference)
        Assumptions.assumeTrue(v.isSound)
        Assumptions.assumeTrue(v.validSequences.mapToSet { seq -> seq.map { it.a } }.size == 5)
        v.validSequences.map { seq -> seq.map { it.a } }.forEach { println(it) }
        val log = logFromModel(reference)
        val hmp = OfflineHeuristicMiner(
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hmp.processLog(log)
        val hmg = OfflineHeuristicMiner(
            bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hmg.processLog(log)
        val cmp1 = CausalNetTraceComparison(reference, hmp.result)
        val cmp2 = CausalNetTraceComparison(reference, hmg.result)
        println(reference)
        println(hmp.result)
        println(hmg.result)
        assertTrue { cmp1.equivalent || cmp2.equivalent }
    }

    @Test
    fun `first trace from data-driven_process_discovery-artificial_event_log-0-percent-noise`() {
        val text = "Triage Register Check X-Ray Visit Check Final_Visit Check Prepare"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = BasicDependencyGraphProvider(1),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        val v = CausalNetVerifier().verify(hm.result)
        assertTrue { v.isSound }
        assertTrue {
            v.validLoopFreeSequences.any { seq ->
                seq
                    .filterNot { it.a.isArtificial }
                    .map { it.a.activity } == text.split(" ")
            }
        }
    }

    @Test
    fun `first trace of activities_of_daily_living_of_several_individuals-edited_hh110_weekends`() {
        val text =
            "toilet sleep toilet sleep bathe dress groom medication mealpreperation eatingdrinking cleaning work personalhygiene medication work toilet outdoors toilet sleep toilet work medication outdoors relax personalhygiene medication sleep"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = BasicDependencyGraphProvider(1),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        //only this little verification, because soundness verification OOMs
        val v = CausalNetVerifierImpl(hm.result)
        assertTrue(v.isConnected)
        assertTrue(v.allDependenciesUsed())
    }

    @Test
    fun `subtrace of the first trace of activities_of_daily_living_of_several_individuals-edited_hh110_weekends`() {
        val text =
            "groom medication mealpreperation work personalhygiene medication work toilet outdoors toilet sleep toilet work medication outdoors"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = BasicDependencyGraphProvider(1),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        //only this little verification, because soundness verification OOMs
        val v = CausalNetVerifierImpl(hm.result)
        assertTrue(v.isConnected)
        assertTrue(v.allDependenciesUsed())
    }

    @Test
    fun `some test`() {
        val text = "a b d f d b g"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = BasicDependencyGraphProvider(1),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        val v = CausalNetVerifier().verify(hm.result)
        assertTrue { v.isSound }
    }

    @Test
    fun `sequence that confuses dependency measure`() {
        val text = "m w o m w o w m o"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = BasicDependencyGraphProvider(1),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        val v = CausalNetVerifier().verify(hm.result)
        assertTrue { v.isSound }
    }

    @Test
    fun `simple L2 loop`() {
        val text = "a b a b"
        val log = logFromString(text)
        val hm = OfflineHeuristicMiner(
            dependencyGraphProvider = L2DependencyGraphProvider(1, .25, .25),
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
        val v = CausalNetVerifier().verify(hm.result)
        assertTrue { v.isSound }
    }

    @Ignore("Good luck")
    @Test
    fun `lotta fun`() {
        val log = logFromString("a b b c d")
        val hm = OfflineHeuristicMiner(
            bindingProvider = CompleteBindingProvider(MostParsimoniousHypothesisSelector()),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        hm.processLog(log)
        println(hm.result)
    }

}
