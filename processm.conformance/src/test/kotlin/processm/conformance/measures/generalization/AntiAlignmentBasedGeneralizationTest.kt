package processm.conformance.measures.generalization

import processm.conformance.PetriNets
import processm.core.log.Helpers
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests based on the examples in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
 * on Anti-Alignments.
 */
class AntiAlignmentBasedGeneralizationTest {
    companion object {
        val table1 = Helpers.logFromString(
            (1..1207).joinToString("\n", postfix = "\n") { "A B D E I" } +
                    (1..145).joinToString("\n", postfix = "\n") { "A C D G H F I" } +
                    (1..56).joinToString("\n", postfix = "\n") { "A C G D H F I" } +
                    (1..23).joinToString("\n", postfix = "\n") { "A C H D F I" } +
                    (1..28).joinToString("\n", postfix = "\n") { "A C D H F I" }
        )
    }

    @Test
    fun `example 6`() {
        // the original value in the article
        // val traceBasedGeneralization =
        //    (1207 * (1.0 - hypot(3.0 / 6.0, 2.0 / 4.0).coerceAtMost(1.0)) +
        //            145 * (1.0 - sqrt(36.0 / 49.0)) +
        //            56 * (1.0 - sqrt(36.0 / 49.0)) +
        //            23 * (1.0 - hypot(4.0 / 6.0, 1.0 / 6.0)) +
        //            28 * (1.0 - sqrt(25.0 / 36.0))) / 1459.0
        // the value adjusted for the adopted penalty function
        val traceBasedGeneralization =
            (1207 * (1.0 - hypot(0.0 / 6.0, 2.0 / 4.0)) +
                    145 * (1.0 - sqrt(36.0 / 49.0)) +
                    56 * (1.0 - sqrt(36.0 / 49.0)) +
                    23 * (1.0 - hypot(4.0 / 6.0, 1.0 / 6.0)) +
                    28 * (1.0 - sqrt(25.0 / 36.0))) / 1459.0
        val logBasedGeneralization = 1.0 - sqrt(36.0 / 49.0)
        val totalGeneralization = 0.5 * traceBasedGeneralization + 0.5 * logBasedGeneralization

        val generalization = AntiAlignmentBasedGeneralization(PetriNets.fig1)
        assertEquals(traceBasedGeneralization, generalization.traceBasedGeneralization(table1.traces), 1e-6)
        assertEquals(logBasedGeneralization, generalization.logBasedGeneralization(table1.traces), 1e-6)
        assertEquals(totalGeneralization, generalization(table1), 1e-6)
    }
}
