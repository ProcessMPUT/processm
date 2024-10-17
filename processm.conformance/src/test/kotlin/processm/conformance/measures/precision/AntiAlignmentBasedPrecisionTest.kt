package processm.conformance.measures.precision

import processm.conformance.PetriNets
import processm.core.log.Helpers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests based on the examples in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
 * on Anti-Alignments.
 */
class AntiAlignmentBasedPrecisionTest {
    companion object {
        val table1 = Helpers.logFromString(
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
    fun `example 5`() {
        val traceBasedPrecision = 31.0 / 35.0
        val logBasedPrecision = 6.0 / 7.0
        val totalPrecision = 0.5 * traceBasedPrecision + 0.5 * logBasedPrecision

        val precision = AntiAlignmentBasedPrecision(PetriNets.fig1)
        precision(table1)
        assertEquals(traceBasedPrecision, precision.traceBasedPrecision, 1e-6)
        assertEquals(logBasedPrecision, precision.logBasedPrecision, 1e-6)
        assertEquals(totalPrecision, precision(table1), 1e-6)
    }
}
