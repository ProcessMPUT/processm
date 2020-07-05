package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.Exclusive
import processm.core.models.processtree.Parallel
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.Sequence
import processm.miners.heuristicminer.Helper.logFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OfflineInductiveMinerTest {
    @Test
    fun `PM book Figure 2-6 | Figure 7-26`() {
        val log = logFromString(
            """
            A B D E H
            A D C E G
            A C D E F B D E G
            A D B E H
            A C D E F D C E F C D E H
            A C D E G
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))", model.toString())
    }

    @Test
    fun `PM book Figure 2-7`() {
        val log = logFromString(
            """
            A B D E H
            A D B E H
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))

        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,∧(B,D),E,H)", model.toString())
    }

    @Test
    fun `PM book Figure 2-7 with moved E activity - prove start activities correctly recognized`() {
        val log = logFromString(
            """
            A E B D H
            A E D B H
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,E,∧(B,D),H)", model.toString())
    }

    @Test
    fun `PM book Figure 7-3`() {
        val log = logFromString(
            """
            A E
            A B E
            A C E
            A D E
            A D D E
            A D D D E
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(B,C,⟲(D,τ),τ),E)", model.toString())
    }

    @Test
    fun `PM book Figure 7-20`() {
        val log = logFromString(
            """
             A B C D
             A B C D
             A B C D
             A C B D
             A C B D
             A E D
            """.trimIndent()
        )
        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(∧(B,C),E),D)", model.toString())

        with(model.root!!) {
            assert(this is Sequence)
            assertEquals(children.size, 3)

            with(children[0] as ProcessTreeActivity) {
                assertEquals(name, "A")
                assertTrue(children.isEmpty())
            }

            with(children[1]) {
                assert(this is Exclusive)
                assertEquals(children.size, 2)

                with(children[0]) {
                    assert(this is Parallel)
                    assertEquals(children.size, 2)

                    with(children[0] as ProcessTreeActivity) {
                        assertEquals(name, "B")
                        assertTrue(children.isEmpty())
                    }

                    with(children[1] as ProcessTreeActivity) {
                        assertEquals(name, "C")
                        assertTrue(children.isEmpty())
                    }
                }

                with(children[1] as ProcessTreeActivity) {
                    assertEquals(name, "E")
                    assertTrue(children.isEmpty())
                }
            }

            with(children[2] as ProcessTreeActivity) {
                assertEquals(name, "D")
                assertTrue(children.isEmpty())
            }
        }
    }

    @Test
    fun `PM book Figure 7-24`() {
        val log = logFromString(
            """
             A B C D
             A B C D
             A B C D
             A C B D
             A C B D
             A C B D
             A C B D
             A B C E F B C D
             A B C E F B C D
             A C B E F B C D
             A C B E F B C D
             A B C E F C B D
             A C B E F B C E F C B D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,⟲(∧(B,C),→(E,F)),D)", model.toString())
    }

    @Test
    fun `PM book Figure 6-5 | 7-29 Q3`() {
        val log = logFromString(
            """
            A B C D E F B D C E G
            A B D C E G
            A B D C E G
            A B C D E F B C D E F B D C E G
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,⟲(→(B,∧(C,D),E),F),G)", model.toString())
    }

    @Test
    fun `PM book Figure 6-6 | 7-29 Q4`() {
        val log = logFromString(
            """
            A C D
            B C D
            A C E
            B C E
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(×(A,B),C,×(D,E))", model.toString())
    }

    @Test
    fun `PM book Figure 6-8 | 7-29 Q5`() {
        val log = logFromString(
            """
            A B E F
            A B E F
            A B E C D B F
            A B E C D B F
            A B E C D B F
            A B C E D B F
            A B C E D B F
            A B C D E B F
            A B C D E B F
            A B C D E B F
            A B C D E B F
            A E B C D B F
            A E B C D B F
            A E B C D B F
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,∧(⟲(B,→(C,D)),E),F)", model.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q6`() {
        val log = logFromString(
            """
            A C E G
            A C E G
            A E C G
            A E C G
            A E C G
            B D F G
            B D F G
            B F D G
            B F D G
            B F D G
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)", model.toString())
    }

    @Test
    fun `Based on PM book Figure 7-29 Q6 - extra activities in sequence`() {
        val log = logFromString(
            """
            A C E G X Y Z
            A C E G X Y Z
            A E C G X Y Z
            A E C G X Y Z
            A E C G X Y Z
            B D F G X Y Z
            B D F G X Y Z
            B F D G X Y Z
            B F D G X Y Z
            B F D G X Y Z
            B F D G X Y Z
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G,X,Y,Z)", model.toString())
    }

    @Test
    fun `PM book Figure 6-11 | 7-29 Q7`() {
        val log = logFromString(
            """
            A C
            A C
            A B C
            A B C
            A B C
            A B B C
            A B B C
            A B B B B C
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,⟲(τ,B),C)", model.toString())
    }

    @Test
    fun `PM book Figure 6-12 | 7-29 Q8`() {
        val log = logFromString(
            """
            A B D
            A B D
            A B D
            A B C B D
            A B C B D
            A B C B C B D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        println(model.successAnalyzedTracesIds)
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,⟲(B,C),D)", model.toString())
    }

    @Test
    fun `Optional activity as first in loop`() {
        val log = logFromString(
            """
            A B D
            A C B D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(C,τ),B,D)", model.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q9`() {
        val log = logFromString(
            """
            A C D
            B C E
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(×(A,B),C,×(D,E))", model.toString())
    }

    @Test
    fun `PM book Figure 7-29 Q10`() {
        val log = logFromString(
            """
            A A
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("⟲(A,τ)", model.toString())
    }

    @Test
    fun `PM book Figure 6-21 | 7-29 Q11`() {
        val log = logFromString(
            """
            A C
            A B C
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(B,τ),C)", model.toString())
    }

    @Test
    fun `PM book Figure 7-30 F1`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A E F D
            A E D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(∧(B,C),→(E,×(F,τ))),D)", model.toString())
    }

    @Test
    fun `Exclusive cut should be able to add tau if trace support not sum to parent's support`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A E F D
            A E D
            A D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,×(∧(B,C),→(E,×(F,τ)),τ),D)", model.toString())
    }

    @Test
    fun `Optional activity in parallel cut`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A C D
            """.trimIndent()
        )

        val inductiveMiner = OfflineInductiveMiner()
        val model = inductiveMiner.processLog(sequenceOf(log))
        val analyzer = PerformanceAnalyzer(model)
        log.traces.forEach { analyzer.analyze(it) }
        assertEquals(log.traces.count(), model.successAnalyzedTracesIds.size)

        assertEquals("→(A,∧(×(B,τ),C),D)", model.toString())
    }
}