package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelSimplifierTest {
    @Test
    fun `Remove tau from sequence with two activities`() {
        val tree = processTree {
            Sequence(
                SilentActivity(),
                Activity("A"),
                SilentActivity(),
                SilentActivity(),
                Activity("B"),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 2)

            assertEquals(children[0], Activity("A"))
            assertEquals(children[1], Activity("B"))
        }

        val expectedTree = processTree {
            Sequence(
                Activity("A"),
                Activity("B")
            )
        }
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Remove tau and generate sequence with one activity`() {
        val tree = processTree {
            Sequence(
                SilentActivity(),
                Activity("C")
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 1)

            assertEquals(children[0], Activity("C"))
        }

        val expectedTree = processTree {
            Sequence(
                Activity("C")
            )
        }
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Can not remove tau if only child`() {
        val tree = processTree {
            Sequence(
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        with(tree.root!!) {
            assertEquals(children.size, 1)

            assertEquals(children[0], SilentActivity())
        }
    }

    @Test
    fun `Remove tau from sequence and parallel but not from other operators`() {
        val tree = processTree {
            RedoLoop(
                SilentActivity(),
                Exclusive(
                    SilentActivity(),
                    Activity("A")
                ),
                Parallel(
                    Activity("B"),
                    SilentActivity(),
                    Activity("C")
                ),
                Activity("D"),
                Sequence(
                    SilentActivity(),
                    Activity("E"),
                    Sequence(
                        Activity("F"),
                        Activity("G"),
                        SilentActivity()
                    )
                )
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                SilentActivity(),
                Exclusive(
                    SilentActivity(),
                    Activity("A")
                ),
                Parallel(
                    Activity("B"),
                    Activity("C")
                ),
                Activity("D"),
                Sequence(
                    Activity("E"),
                    Sequence(
                        Activity("F"),
                        Activity("G")
                    )
                )
            )
        }

        assertEquals(tree.toString(), "⟲(τ,×(τ,A),∧(B,C),D,→(E,→(F,G)))")
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `In redo loop can remove duplicated silent activities but only in loop part`() {
        val tree = processTree {
            RedoLoop(
                SilentActivity(),
                Activity("A"),
                SilentActivity(),
                SilentActivity(),
                SilentActivity(),
                Activity("C"),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                SilentActivity(),
                Activity("A"),
                SilentActivity(),
                Activity("C")
            )
        }

        assertEquals("⟲(τ,A,τ,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-Loop with not tau as first element`() {
        val tree = processTree {
            RedoLoop(
                Activity("A"),
                SilentActivity(),
                SilentActivity(),
                SilentActivity(),
                Activity("C"),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                Activity("A"),
                SilentActivity(),
                Activity("C")
            )
        }

        assertEquals("⟲(A,τ,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop without any silent activity`() {
        val tree = processTree {
            RedoLoop(
                Activity("A"),
                Activity("B"),
                Activity("C")
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                Activity("A"),
                Activity("B"),
                Activity("C")
            )
        }

        assertEquals("⟲(A,B,C)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only silent activity`() {
        val tree = processTree {
            RedoLoop(
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                SilentActivity()
            )
        }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only two silent activity`() {
        val tree = processTree {
            RedoLoop(
                SilentActivity(),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                SilentActivity()
            )
        }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with only silent activities`() {
        val tree = processTree {
            RedoLoop(
                SilentActivity(),
                SilentActivity(),
                SilentActivity(),
                SilentActivity(),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                SilentActivity()
            )
        }

        assertEquals("⟲(τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Redo-loop with silent and non silent activities`() {
        val tree = processTree {
            RedoLoop(
                Activity("A"),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            RedoLoop(
                Activity("A"),
                SilentActivity()
            )
        }

        assertEquals("⟲(A,τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Ignore action if root is activity (activity as root in process tree)`() {
        val model = processTree { Activity("A") }
        ModelSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `In exclusive choice operator can remove duplicated silent activities`() {
        val tree = processTree {
            Exclusive(
                SilentActivity(),
                Activity("A"),
                SilentActivity(),
                SilentActivity(),
                Activity("C"),
                SilentActivity()
            )
        }

        ModelSimplifier().reduceTauLeafs(tree)

        val expectedTree = processTree {
            Exclusive(
                SilentActivity(),
                Activity("A"),
                Activity("C")
            )
        }

        assertEquals(tree.toString(), "×(τ,A,C)")
        assertTrue(tree.languageEqual(expectedTree))
    }

    @Test
    fun `Tree without nodes can not be simpler`() {
        val model = processTree { null }
        ModelSimplifier().simplify(model)

        assertNull(model.root)
    }

    @Test
    fun `Tree with single activity can not be simpler`() {
        val model = processTree { Activity("A") }
        ModelSimplifier().simplify(model)

        assertEquals(Activity("A"), model.root)
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `Tree with empty operator as root can be replaced with silent activity`() {
        val model = processTree { Exclusive() }
        ModelSimplifier().simplify(model)

        assertEquals("τ", model.toString())
        assertTrue(model.languageEqual(processTree { SilentActivity() }))
    }

    @Test
    fun `Tree with operator contain single activity can be simplify to just one activity`() {
        val model = processTree {
            Sequence(
                Activity("A")
            )
        }
        ModelSimplifier().simplify(model)

        assertEquals(Activity("A"), model.root)
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `Move activity to parent if only child in operator node`() {
        val model = processTree {
            Exclusive(
                Activity("A"),
                Sequence(
                    Activity("B")
                )
            )
        }
        ModelSimplifier().simplify(model)

        val expected = processTree {
            Exclusive(
                Activity("A"),
                Activity("B")
            )
        }
        assertEquals("×(A,B)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Empty operator can be replaced by silent activity`() {
        val model = processTree {
            Exclusive(
                Activity("A"),
                Sequence()
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Exclusive(
                Activity("A"),
                SilentActivity()
            )
        }
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Redo loop with single activity can be transform to just activity`() {
        val model = processTree {
            RedoLoop(
                Activity("A")
            )
        }

        ModelSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `Redo loop operator transformed to loop with activity and tau`() {
        val model = processTree {
            RedoLoop(
                Activity("A"),
                Sequence(),
                SilentActivity()
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            RedoLoop(
                Activity("A"),
                SilentActivity()
            )
        }
        assertEquals("⟲(A,τ)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Redo loop operator with tau only can be simplify to tau`() {
        val model = processTree {
            RedoLoop(
                SilentActivity(),
                SilentActivity()
            )
        }

        ModelSimplifier().simplify(model)

        assertEquals("τ", model.toString())
        assertTrue(model.languageEqual(processTree { SilentActivity() }))
    }

    @Test
    fun `Sequence with activity and tau is equal to just alone activity`() {
        val model = processTree {
            Sequence(
                Activity("A"),
                SilentActivity()
            )
        }

        ModelSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `Exclusive choice with activity and empty operator`() {
        val model = processTree {
            Exclusive(
                Activity("A"),
                Exclusive()
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Exclusive(
                Activity("A"),
                SilentActivity()
            )
        }
        assertEquals("×(A,τ)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Parallel operator activity and empty operator`() {
        val model = processTree {
            Parallel(
                Activity("A"),
                Exclusive()
            )
        }

        ModelSimplifier().simplify(model)

        assertEquals("A", model.toString())
        assertTrue(model.languageEqual(processTree { Activity("A") }))
    }

    @Test
    fun `Move up activity if exclusive choice operators`() {
        val model = processTree {
            Exclusive(
                Activity("A"),
                Exclusive(
                    Activity("B"),
                    Activity("C")
                )
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Exclusive(
                Activity("A"),
                Activity("B"),
                Activity("C")
            )
        }
        assertEquals("×(A,B,C)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Move up activity if parallel operators`() {
        val model = processTree {
            Parallel(
                Activity("A"),
                Parallel(
                    Activity("B"),
                    Activity("C"),
                    Parallel(
                        Activity("D"),
                        Activity("E")
                    )
                ),
                Activity("F")
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Parallel(
                Activity("A"),
                Activity("B"),
                Activity("C"),
                Activity("D"),
                Activity("E"),
                Activity("F")
            )
        }
        assertEquals("∧(A,B,C,D,E,F)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Validate indexes`() {
        val model = processTree {
            Parallel(
                Parallel(
                    Parallel(
                        Activity("A"),
                        Activity("B")
                    ),
                    Activity("C"),
                    Activity("D")
                ),
                Activity("E")
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Parallel(
                Activity("A"),
                Activity("B"),
                Activity("C"),
                Activity("D"),
                Activity("E")
            )
        }
        assertEquals("∧(A,B,C,D,E)", model.toString())
        assertTrue(model.languageEqual(expected))
    }

    @Test
    fun `Move up activity if sequence operators`() {
        val model = processTree {
            Sequence(
                Activity("A"),
                Sequence(
                    Activity("B"),
                    Parallel(
                        Activity("D"),
                        Activity("E"),
                        Activity("I")
                    ),
                    Activity("C")
                ),
                Activity("F"),
                Sequence(
                    Activity("G"),
                    Activity("H")
                )
            )
        }

        ModelSimplifier().simplify(model)

        val expected = processTree {
            Sequence(
                Activity("A"),
                Activity("B"),
                Parallel(
                    Activity("D"),
                    Activity("E"),
                    Activity("I")
                ),
                Activity("C"),
                Activity("F"),
                Activity("G"),
                Activity("H")
            )
        }
        assertEquals("→(A,B,∧(D,E,I),C,F,G,H)", model.toString())
        assertTrue(model.languageEqual(expected))
    }
}