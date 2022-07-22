package processm.experimental.conformance.models.antialignments

import processm.conformance.PetriNets
import processm.core.log.Helpers.logFromString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests based on the examples in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
 * on Anti-Alignments.
 */
class DFSPetriNetTests {
    companion object {
        val table1 = logFromString(
            """
            A B D E I
            A C D G H F I
            A C G D H F I
            A C H D F I
            A C D H F I
            """.trimIndent()
        )
    }

    @Test
    fun `example 1`() {
        val expectedModelMoves = listOf("A", "C", "G", "H", "D", "F", "I")
        val expectedCost = 1

        val antiAlignments = DFS(PetriNets.fig1).align(table1.traces, 7)
        println(antiAlignments)

        val antiAlignment = antiAlignments.first()
        assertEquals(expectedCost, antiAlignment.cost)
        assertContentEquals(expectedModelMoves, antiAlignment.steps.map { it.modelMove?.name })
    }

    @Test
    @Ignore("Example 2 uses incomplete anti-alignment; our implementation calculates complete anti-alignments only.")
    fun `example 2`() {
        val expectedModelMoves = listOf("A", "B")
        val expectedCost = 3

        val antiAlignments = DFS(PetriNets.fig2).align(table1.traces, 2)
        println(antiAlignments)

        val antiAlignment = antiAlignments.first()
        assertEquals(expectedCost, antiAlignment.cost)
        assertContentEquals(expectedModelMoves, antiAlignment.steps.map { it.modelMove?.name })
    }

    @Test
    @Ignore("Does not terminate in a reasonable amount of time.")
    fun `example 3`() {
        val expectedModelMoves = listOf("B", "A", "A", "A", "A", "A", "A", "A", "A")
        val expectedCost = 1

        val antiAlignments = DFS(PetriNets.fig3).align(table1.traces, 9)
        println(antiAlignments)

        val antiAlignment = antiAlignments.first()
        assertEquals(expectedCost, antiAlignment.cost)
        assertContentEquals(expectedModelMoves, antiAlignment.steps.map { it.modelMove?.name })
    }
}
