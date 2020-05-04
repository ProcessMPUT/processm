package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessTreeSimplifierTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")
    private val F = ProcessTreeActivity("F")
    private val G = ProcessTreeActivity("G")
    private val H = ProcessTreeActivity("H")
    private val I = ProcessTreeActivity("I")
    private val silentActivity = SilentActivity()

    @Test
    fun `Remove tau from sequence with two activities`() {
        val tree = processTree {
            Sequence(
                silentActivity,
                A,
                silentActivity,
                silentActivity,
                B,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 2)

            assertEquals(children[0], A)
            assertEquals(children[1], B)
        }

        val expectedTree = processTree { Sequence(A, B) }
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Remove tau and generate sequence with one activity`() {
        val tree = processTree { Sequence(silentActivity, C) }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 1)

            assertEquals(children[0], C)
        }

        val expectedTree = processTree { Sequence(C) }
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Can not remove tau if only child`() {
        val tree = processTree { Sequence(silentActivity) }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 1)

            assertEquals(children[0], silentActivity)
        }
    }

    @Test
    fun `Remove tau from sequence and parallel but not from other operators`() {
        val tree = processTree {
            RedoLoop(
                silentActivity,
                Exclusive(silentActivity, A),
                Parallel(B, silentActivity, C),
                D,
                Sequence(
                    silentActivity,
                    E,
                    Sequence(F, G, silentActivity)
                )
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                silentActivity,
                Exclusive(silentActivity, A),
                Parallel(B, C),
                D,
                Sequence(E, Sequence(F, G))
            )
        }

        assertEquals(tree.toString(), "⟲(τ,×(τ,A),∧(B,C),D,→(E,→(F,G)))")
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `In redo loop can remove duplicated silent activities but only in loop part`() {
        val tree = processTree {
            RedoLoop(
                silentActivity,
                A,
                silentActivity,
                silentActivity,
                silentActivity,
                C,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                silentActivity,
                A,
                silentActivity,
                C
            )
        }

        assertEquals("⟲(τ,A,τ,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-Loop with not tau as first element`() {
        val tree = processTree {
            RedoLoop(
                A,
                silentActivity,
                silentActivity,
                silentActivity,
                C,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(A, silentActivity, C) }

        assertEquals("⟲(A,τ,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop without any silent activity`() {
        val tree = processTree { RedoLoop(A, B, C) }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(A, B, C) }

        assertEquals("⟲(A,B,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only silent activity`() {
        val tree = processTree { RedoLoop(silentActivity) }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(silentActivity) }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only two silent activity`() {
        val tree = processTree {
            RedoLoop(
                silentActivity,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(silentActivity) }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only silent activities`() {
        val tree = processTree {
            RedoLoop(
                silentActivity,
                silentActivity,
                silentActivity,
                silentActivity,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(silentActivity) }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with silent and non silent activities`() {
        val tree = processTree { RedoLoop(A, silentActivity) }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { RedoLoop(A, silentActivity) }

        assertEquals("⟲(A,τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Ignore action if root is activity (activity as root in process tree)`() {
        val model = processTree { A }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `In exclusive choice operator can remove duplicated silent activities`() {
        val tree = processTree {
            Exclusive(
                silentActivity,
                A,
                silentActivity,
                silentActivity,
                C,
                silentActivity
            )
        }

        ProcessTreeSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree { Exclusive(SilentActivity(), A, C) }

        assertEquals(tree.toString(), "×(τ,A,C)")
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Tree without nodes can not be simpler`() {
        val model = processTree { null }
        ProcessTreeSimplifier().simplify(model)

        assertNull(model.root)
    }

    @Test
    fun `Tree with single activity can not be simpler`() {
        val model = processTree { A }
        ProcessTreeSimplifier().simplify(model)

        assertEquals(A, model.root)
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `Tree with empty operator as root can be replaced with silent activity`() {
        val model = processTree { Exclusive() }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("τ", model.toString())
        assertTrue(model.languageEqual(processTree { silentActivity }))
    }

    @Test
    fun `Tree with operator contain single activity can be simplify to just one activity`() {
        val model = processTree { Sequence(A) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals(A, model.root)
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `Move activity to parent if only child in operator node`() {
        val model = processTree { Exclusive(A, Sequence(B)) }
        ProcessTreeSimplifier().simplify(model)

        val expected = processTree { Exclusive(A, B) }

        assertEquals("×(A,B)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Empty operator can be replaced by silent activity`() {
        val model = processTree { Exclusive(A, Sequence()) }
        ProcessTreeSimplifier().simplify(model)

        val expected = processTree { Exclusive(A, silentActivity) }

        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Redo loop with single activity can be transform to just activity`() {
        val model = processTree { RedoLoop(A) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `Redo loop operator transformed to loop with activity and tau`() {
        val model = processTree { RedoLoop(A, Sequence(), silentActivity) }
        ProcessTreeSimplifier().simplify(model)

        val expected = processTree { RedoLoop(A, silentActivity) }

        assertEquals("⟲(A,τ)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Redo loop operator with tau only can be simplify to tau`() {
        val model = processTree {
            RedoLoop(
                silentActivity,
                silentActivity
            )
        }

        ProcessTreeSimplifier().simplify(model)

        assertEquals("τ", model.toString())
        assertTrue(model.languageEqual(processTree { silentActivity }))
    }

    @Test
    fun `Sequence with activity and tau is equal to just alone activity`() {
        val model = processTree { Sequence(A, silentActivity) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `Exclusive choice with activity and empty operator`() {
        val model = processTree { Exclusive(A, Exclusive()) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("×(A,τ)", model.toString())
        assertTrue(model.languageEqual(processTree { Exclusive(A, silentActivity) }))
    }

    @Test
    fun `Parallel operator activity and empty operator`() {
        val model = processTree { Parallel(A, Exclusive()) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { A }))
    }

    @Test
    fun `Move up activity if exclusive choice operators`() {
        val model = processTree { Exclusive(A, Exclusive(B, C)) }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("×(A,B,C)", model.toString())
        assertTrue(model.languageEqual(processTree { Exclusive(A, B, C) }))
    }

    @Test
    fun `Move up activity if parallel operators`() {
        val model = processTree {
            Parallel(
                A,
                Parallel(
                    B,
                    C,
                    Parallel(
                        D,
                        E
                    )
                ),
                F
            )
        }
        ProcessTreeSimplifier().simplify(model)

        assertEquals("∧(A,B,C,D,E,F)", model.toString())
        assertTrue(model.languageEqual(processTree { Parallel(A, B, C, D, E, F) }))
    }

    @Test
    fun `Validate indexes`() {
        val model = processTree {
            Parallel(
                Parallel(
                    Parallel(
                        A,
                        B
                    ),
                    C,
                    D
                ),
                E
            )
        }

        ProcessTreeSimplifier().simplify(model)

        assertEquals("∧(A,B,C,D,E)", model.toString())
        assertTrue(model.languageEqual(processTree { Parallel(A, B, C, D, E) }))
    }

    @Test
    fun `Move up activity if sequence operators`() {
        val model = processTree {
            Sequence(
                A,
                Sequence(
                    B,
                    Parallel(
                        D,
                        E,
                        I
                    ),
                    C
                ),
                F,
                Sequence(
                    G,
                    H
                )
            )
        }

        ProcessTreeSimplifier().simplify(model)

        val expected = processTree {
            Sequence(
                A,
                B,
                Parallel(
                    D,
                    E,
                    I
                ),
                C,
                F,
                G,
                H
            )
        }
        assertEquals("→(A,B,∧(D,E,I),C,F,G,H)", model.toString())
        assertTrue(model.languageEqual(expected))
    }
}