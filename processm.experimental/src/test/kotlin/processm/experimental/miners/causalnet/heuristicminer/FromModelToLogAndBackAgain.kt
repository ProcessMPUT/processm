package processm.experimental.miners.causalnet.heuristicminer

import processm.core.helpers.mapToSet
import processm.core.log.Helpers.event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.verifiers.CausalNetVerifier
import processm.miners.causalnet.CausalNetMiner
import processm.miners.causalnet.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.causalnet.heuristicminer.bindingproviders.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.causalnet.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class FromModelToLogAndBackAgain {
    val a = Node("a")
    val b = Node("b")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val b3 = Node("b3")
    val c = Node("c")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val d3 = Node("d3")
    val e = Node("e")

    private fun str(inp: Set<List<Node>>): String {
        return inp.map { seq -> seq.map { it.activity } }.toString()
    }

    private fun test(reference: CausalNet, hm: CausalNetMiner) {
        logger().debug("REFERENCE:\n${reference}")
        val referenceVerifier = CausalNetVerifier().verify(reference)
        val expectedSequences =
            referenceVerifier.validSequences.mapToSet { seq -> seq.map { it.a }.filter { !it.isSilent } }
        logger().debug("EXPECTED SEQUENCES: ${str(expectedSequences)}")
        assertTrue(referenceVerifier.noDeadParts)
        assertTrue(referenceVerifier.isSound)
        val log = Log(referenceVerifier
            .validSequences
            .map { seq -> Trace(seq.asSequence().map { ab -> event(ab.a.activity) }) })
        log.traces.forEach { println(it.events.toList()) }
        hm.processLog(log)
        val v = CausalNetVerifier().verify(hm.result)

        val actualSequences = v.validSequences.mapToSet { seq -> seq.map { ab -> ab.a }.filter { !it.isSilent } }
        logger().debug("ACTUAL SEQUENCES: ${str(actualSequences)}")
        logger().debug("UNEXPECTED SEQUENCES: ${str(actualSequences - expectedSequences)}")
        logger().debug("MISSING SEQUENCES: ${str(expectedSequences - actualSequences)}")
        logger().debug("MODEL:\n" + hm.result)
        assertEquals(expectedSequences, actualSequences)
        assertTrue(v.noDeadParts)
        assertTrue(v.isSound)
    }

    /**
     * If first X bs are executed, then exactly X ds are executed.
     *
     * Without mining long-term dependencies, heuristic miner is not able to even produce a sound model
     */
    private val sequentialCounting = causalnet {
        start = a
        end = e
        a splits b1
        b1 splits b2 or c + e
        b2 splits b3 or c + e
        b3 splits c + e
        c splits d1
        d1 splits d2 or e
        d2 splits d3 or e
        d3 splits e
        a joins b1
        b1 joins b2
        b2 joins b3
        b1 or b2 or b3 join c
        c joins d1
        d1 joins d2
        d2 joins d3
        b1 + d1 or b2 + d2 or b3 + d3 join e
    }

    @Ignore("See #97")
    @Test
    fun `Online - Naive - sequential counting`() {
        test(
            sequentialCounting,
            OnlineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()
            )
        )
    }

    /**
     * A simple test inspired by the paper "Flexible heuristic miner"
     */
    private val fhm = causalnet {
        start = a
        end = e
        a splits b1 or b2
        b1 splits c + d1
        b2 splits c + d2
        c splits d1 or d2
        d1 splits e
        d2 splits e
        a joins b1
        a joins b2
        b1 or b2 join c
        b1 + c join d1
        b2 + c join d2
        d1 or d2 join e
    }

    @Ignore("See #97")
    @Test
    fun `Online - Naive - flexible heurisitc miner long-distance dependency example`() {
        test(
            fhm,
            OnlineHeuristicMiner(
                longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner(),
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector())
            )
        )
    }
}
