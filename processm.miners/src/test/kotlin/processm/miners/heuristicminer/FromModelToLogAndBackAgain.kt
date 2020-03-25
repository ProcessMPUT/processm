package processm.miners.heuristicminer

import org.opentest4j.AssertionFailedError
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.verifiers.CausalNetVerifier
import processm.miners.heuristicminer.Helper.event
import processm.miners.heuristicminer.longdistance.avoidability.ValidSequenceBasedAvoidabilityChecker
import processm.miners.heuristicminer.bindingproviders.CompleteBindingProvider
import processm.miners.heuristicminer.bindingproviders.hypothesisselector.MostGreedyHypothesisSelector
import processm.miners.heuristicminer.longdistance.BruteForceLongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.NaiveLongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.ShortestUniquePrefixLongDistanceDependencyMiner
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import kotlin.test.*


class FromModelToLogAndBackAgain {
    val a = Node("a")
    val b = Node("b")
    val b0 = Node("b0")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val b3 = Node("b3")
    val c = Node("c")
    val d = Node("d")
    val d0 = Node("d0")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val d3 = Node("d3")
    val d4 = Node("d4")
    val d5 = Node("d5")
    val d6 = Node("d6")
    val d7 = Node("d7")
    val e = Node("e")

    private fun str(inp: Set<List<Node>>): String {
        return inp.map { seq -> seq.map { it.activity } }.toString()
    }

    private fun test(reference: Model, hm: AbstractHeuristicMiner) {
        logger().debug("REFERENCE:\n${reference}")
        val referenceVerifier = CausalNetVerifier().verify(reference)
        val expectedSequences =
            referenceVerifier.validSequences.map { seq -> seq.map { it.a }.filter { !it.special } }.toSet()
        logger().debug("EXPECTED SEQUENCES: ${str(expectedSequences)}")
        assertTrue(referenceVerifier.noDeadParts)
        assertTrue(referenceVerifier.isSound)
        val log = Log(referenceVerifier
            .validSequences
            .map { seq -> Trace(seq.asSequence().map { ab -> event(ab.a.activity) }) })
        log.traces.forEach { println(it.events.toList()) }
        hm.processLog(log)
        val v = CausalNetVerifier().verify(hm.result)

        val actualSequences = v.validSequences.map { seq -> seq.map { ab -> ab.a }.filter { !it.special } }.toSet()
        logger().debug("ACTUAL SEQUENCES: ${str(actualSequences)}")
        logger().debug("UNEXPECTED SEQUENCES: ${str(actualSequences - expectedSequences)}")
        logger().debug("MISSING SEQUENCES: ${str(expectedSequences - actualSequences)}")
        logger().debug("MODEL:\n" + hm.result)
        assertEquals(expectedSequences, actualSequences)
        assertTrue(v.noDeadParts)
        assertTrue(v.isSound)
    }

    private val hell = causalnet {
        start = a
        end = e
        a splits b1 or b2 or b1 + b2
        b1 splits c + e or c + d
        b2 splits c + e or c + d
        c splits d or e
        d splits e
        a joins b1
        a joins b2
        b1 or b2 or b1 + b2 join c
        b1 + b2 + c join d
        c + b1 or c + b2 or d join e
    }

    @Test
    fun `Offline - Brute - hell of long-term dependencies`() {
        test(
            hell,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Test
    @Ignore
    fun `Offline - Naive - hell of long-term dependencies`() {
        test(hell, OfflineHeuristicMiner(longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()))
    }

    private val reversedHell = causalnet {
        start = a
        end = e
        a splits c + d1 or b or c + d2
        b splits d1 + c + d2
        c splits d1 or d2 or d1 + d2
        d1 splits e
        d2 splits e
        a joins b
        a or b join c
        b + c or a + c join d1
        b + c or a + c join d2
        d1 or d2 or d1 + d2 join e
    }

    @Test
    fun `Offline - Brute - reversed hell of long-term dependencies`() {
        test(
            reversedHell,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Test
    @Ignore("Naive approach is too simple")
    fun `Offline - Naive - reversed hell of long-term dependencies`() {
        test(reversedHell, OfflineHeuristicMiner(longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()))
    }


    /**
     * Intended sequences:
     * a b1 c e
     * a b2 c e
     * a (b1 || b2) c (d1 || d2) e (where || denotes parallel execution, i.e., arbitrary order)
     * a c d1 e
     * a c d2 e
     */
    private val treachery = causalnet {
        start = a
        end = e
        a splits b1 + e or b2 + e or c + d1 + d2 + b1 + b2 or c + d1 + e or c + d2 + e
        b1 splits c
        b2 splits c
        c splits d1 or e or d2 or d1 + d2
        d1 splits e
        d2 splits e
        a joins b1
        a joins b2
        a or b1 or b2 or a + b1 + b2 join c
        a + c join d1
        a + c join d2
        a + c or d1 + d2 or a + d1 or a + d2 join e
    }

    @Test
    fun `Offline - Brute - treachery 9th circle of hell`() {
        test(
            treachery,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Test
    @Ignore("Naive approach is too simple")
    fun `Offline - Naive - treachery 9th circle of hell`() {
        test(
            treachery,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()
            )
        )
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

    @Test
    fun `Offline - Brute - sequential counting`() {
        test(
            sequentialCounting,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Test
    fun `Offline - Naive - sequential counting`() {
        test(
            sequentialCounting,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()
            )
        )
    }

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
    Any non-zero number X of bs may be executed in parallel, but then dX must be executed (e.g., if exactly 2 (any) bs were executed, then d2 must be executed)
     */
    private val numberIndicator = causalnet {
        start = a
        end = e
        a splits b1 + d1 or b2 + d1 or b3 + d1
        a splits b1 + b2 + d2 or b1 + b3 + d2 or b2 + b3 + d2
        a splits b1 + b2 + b3 + d3
        b1 splits c + d1 or c + d2 or c + d3
        b2 splits c + d1 or c + d2 or c + d3
        b3 splits c + d1 or c + d2 or c + d3
        c splits d1 or d2 or d3
        d1 splits e
        d2 splits e
        d3 splits e
        a joins b1
        a joins b2
        a joins b3
        b1 or b2 or b3 or b1 + b2 or b1 + b3 or b2 + b3 or b1 + b2 + b3 join c
        a + b1 + c or a + b2 + c or a + b3 + c join d1
        a + b1 + b2 + c or a + b1 + b3 + c or a + b2 + b3 + c join d2
        a + b1 + b2 + b3 + c join d3
        d1 or d2 or d3 join e
    }

    @Test
    @Ignore("Hypothesis space is too small, as the underlying model also depends on a task not being executed")
    fun `Offline - Brute - number indicator`() {
        test(
            numberIndicator,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Ignore("Naive approach is too simple")
    @Test
    fun `Offline - Naive - number indicator`() {
        test(
            numberIndicator,
            OfflineHeuristicMiner(
                longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()
            )
        )
    }

    /**
     * First N bs is executed in parallel, then first N ds is to be executed in parallel.
     */
    private val firstNInParallel = causalnet {
        start = a
        end = e
        a splits b1 or b1 + b2 or b1 + b2 + b3
        b1 splits c + d1
        b2 splits c + d2
        b3 splits c + d3
        c splits d1 or d1 + d2 or d1 + d2 + d3
        d1 splits e
        d2 splits e
        d3 splits e
        a joins b1
        a joins b2
        a joins b3
        b1 or b1 + b2 or b1 + b2 + b3 join c
        b1 + c join d1
        b2 + c join d2
        b3 + c join d3
        d1 or d1 + d2 or d1 + d2 + d3 join e
    }

    @Test
    fun `Offline - Brute - first n in parallel`() {
        test(
            firstNInParallel,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Ignore("Naive approach is too simple")
    @Test
    fun `Offline - Naive - first n in parallel`() {
        test(firstNInParallel, OfflineHeuristicMiner(longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()))
    }

    /**
     * Any non-zero number N of bs may be executed, and then first N ds must be executed.
     * Both bs and ds are executed in parallel.
     * For example, a valid sequence is `a b3 b2 c d1 d2 e`
     */
    private val parallelCounting = causalnet {
        start = a
        end = e
        a splits b1 or b2 or b3 or b1 + b2 + d2 or b1 + b3 + d2 or b2 + b3 + d2 or b1 + b2 + b3 + d2 + d3
        b1 splits c
        b2 splits c
        b3 splits c
        c splits d1 or d1 + d2 or d1 + d2 + d3
        d1 splits e
        d2 splits e
        d3 splits e
        a joins b1
        a joins b2
        a joins b3
        b1 or b2 or b3 or b1 + b2 or b2 + b3 or b1 + b3 or b1 + b2 + b3 join c
        c joins d1
        a + c join d2
        a + c join d3
        d1 or d1 + d2 or d1 + d2 + d3 join e
    }

    @Test
    @Ignore("Takes too long. Also outside the hypothesis space?")
    fun `Offline - Brute - parallel counting`() {
        test(
            parallelCounting,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    /**
     * Nodes b2, b1, b0 encode a binary number, while nodes d7, .., d0 encode the corresponding unary number.
     * Very hard for mining, because the dependencies are not only on executing, but also on not executing some nodes, e.g.,
     * d1 is executed if, and only if, b0 is executed, but b2 and b1 are not executed.
     */
    private val binaryToUnary = causalnet {
        start = a
        end = e
        a splits c or b0 or b1 or b1 + b0 or b2 or b2 + b0 or b2 + b1 or b2 + b1 + b0
        b0 splits c + d1 or c + d3 or c + d5 or c + d7
        b1 splits c + d2 or c + d3 or c + d6 or c + d7
        b2 splits c + d4 or c + d5 or c + d6 or c + d7
        c splits d0 or d1 or d2 or d3 or d4 or d5 or d6 or d7
        d0 splits e
        d1 splits e
        d2 splits e
        d3 splits e
        d4 splits e
        d5 splits e
        d6 splits e
        d7 splits e
        a joins b0
        a joins b1
        a joins b2
        a or b0 or b1 or b1 + b0 or b2 or b2 + b0 or b2 + b1 or b2 + b1 + b0 join c
        c joins d0
        b0 + c join d1
        b1 + c join d2
        b1 + b0 + c join d3
        b2 + c join d4
        b2 + b0 + c join d5
        b2 + b1 + c join d6
        b2 + b1 + b0 + c join d7
        d0 or d1 or d2 or d3 or d4 or d5 or d6 or d7 join e
    }

    @Ignore
    @Test
    fun `Offline - Brute - binary to unary decoder`() {
        test(
            binaryToUnary,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
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

    @Test
    fun `Offline - Brute - flexible heurisitc miner long-distance dependency example`() {
        test(
            fhm,
            OfflineHeuristicMiner(
                bindingProvider = CompleteBindingProvider(MostGreedyHypothesisSelector()),
                longDistanceDependencyMiner = BruteForceLongDistanceDependencyMiner(
                    ValidSequenceBasedAvoidabilityChecker()
                )
            )
        )
    }

    @Test
    fun `Offline - Naive - flexible heurisitc miner long-distance dependency example`() {
        test(fhm, OfflineHeuristicMiner(longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()))
    }

    @Test
    fun `Offline - Void - flexible heurisitc miner long-distance dependency example`() {
        assertFailsWith<AssertionFailedError> {
            test(fhm, OfflineHeuristicMiner(longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()))
        }
    }

    @Test
    fun `Online - Naive - flexible heurisitc miner long-distance dependency example`() {
        test(fhm, OnlineHeuristicMiner(longDistanceDependencyMiner = NaiveLongDistanceDependencyMiner()))
    }
}