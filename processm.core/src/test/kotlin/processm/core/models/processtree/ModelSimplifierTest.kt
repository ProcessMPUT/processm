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
}