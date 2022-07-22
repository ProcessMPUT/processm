package processm.conformance.models.antialignments

import org.junit.jupiter.api.assertThrows
import processm.conformance.PetriNets
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.models.commons.ProcessModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests based on the examples in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
 * on Anti-Alignments.
 */
class TwoPhaseDFSPetriNetTests {
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

        test(PetriNets.fig1, table1, expectedCost, expectedModelMoves)
    }

    @Test
    fun `example 2`() {
        val expectedModelMoves = listOf("A", "B")
        val expectedCost = 3
        val expectedSize = 2

        val exception = assertThrows<IllegalStateException> {
            test(PetriNets.fig2, table1, expectedCost, expectedModelMoves)
        }

        assertEquals("An anti-alignment within the limit of $expectedSize events does not exist.", exception.message)
    }

    @Test
    fun `example 2 complete`() {
        val expectedModelMoves = listOf("A", "B", "D", "E", "I")
        val expectedCost = 0

        test(PetriNets.fig2, table1, expectedCost, expectedModelMoves)
    }

    @Test
    fun `example 3`() {
        val expectedModelMoves = listOf("G", "G", "G", "G", "G", "G")
        val expectedCost = expectedModelMoves.size + table1.traces.minOf { it.events.count() }

        test(PetriNets.fig3, table1, expectedCost, expectedModelMoves)
    }

    private fun test(
        model: ProcessModel,
        log: Log,
        expectedCost: Int,
        expectedModelMoves: List<String>
    ) {
        val antiAlignments = TwoPhaseDFS(model).align(log, expectedModelMoves.size)
        println(antiAlignments)

        val antiAlignment = antiAlignments.first()
        assertEquals(expectedCost, antiAlignment.cost)
        assertEquals(expectedModelMoves.size, antiAlignment.size)
        assertContentEquals(expectedModelMoves, antiAlignment.steps.mapNotNull { it.modelMove?.name })
    }

}
