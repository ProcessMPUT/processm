package processm.conformance.models.antialignments

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.conformance.PetriNets
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.logging.debug
import processm.core.logging.loggedScope
import processm.core.models.commons.ProcessModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `example 4`() {
        val expectedModelMoves = listOf("A", "C", "D", "G", "H", "F", "I")
        val expectedCost = 0

        test(PetriNets.fig4, table1, expectedCost, expectedModelMoves)
    }

    @ParameterizedTest
    @ValueSource(ints = intArrayOf(0, 1, 2, 3, 4))
    fun `example 5`(dropIndex: Int) = test(
        model = PetriNets.fig1,
        log = Log(table1.traces.withIndex().mapNotNull { (i, v) -> if (i != dropIndex) v else null }),
        expectedCost = listOf(5, 1, 1, 2, 1)[dropIndex],
        expectedModelMoves = listOf(
            listOf("A", "B", "D", "E", "I"),
            listOf("A", "C", "G", "H", "D", "F", "I"),
            listOf("A", "C", "G", "H", "D", "F", "I"),
            listOf("A", "C", "H", "D", "F", "I"),
            listOf("A", "C", "D", "H", "F", "I"),
        )[dropIndex]
    )


    private fun test(
        model: ProcessModel,
        log: Log,
        expectedCost: Int,
        expectedModelMoves: List<String>
    ) = loggedScope { logger ->
        val antiAlignments = TwoPhaseDFS(model).align(log, expectedModelMoves.size)
        logger.debug { antiAlignments.toString() }

        assertTrue(antiAlignments.any { a -> expectedCost == a.cost })
        assertTrue(antiAlignments.any { a -> expectedModelMoves.size == a.size })
        assertTrue(antiAlignments.any { a -> expectedModelMoves == a.steps.mapNotNull { it.modelMove?.name } })
    }

}
