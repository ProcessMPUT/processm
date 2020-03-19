package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals( "⟲(τ,A,τ,C)", tree.toString())
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

        assertEquals( "⟲(A,τ,C)", tree.toString())
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

        assertEquals( "⟲(A,B,C)", tree.toString())
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

        assertEquals( "⟲(τ)", tree.toString())
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
                SilentActivity(),
                SilentActivity()
            )
        }

        assertEquals( "⟲(τ,τ)", tree.toString())
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
                SilentActivity(),
                SilentActivity()
            )
        }

        assertEquals( "⟲(τ,τ)", tree.toString())
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

        assertEquals( "⟲(A,τ)", tree.toString())
        assertTrue(tree.languageEqual(expectedTree))
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
}