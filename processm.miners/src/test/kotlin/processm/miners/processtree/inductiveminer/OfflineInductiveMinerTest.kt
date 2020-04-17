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
    fun `Process Mining - Figure 7 point 20`() {
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
        inductiveMiner.processLog(log)

        assertEquals(inductiveMiner.result.toString(), "→(A,×(∧(B,C),E),D)")

        with(inductiveMiner.result.root!!) {
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
}