package processm.conformance.measures.precision

import org.junit.jupiter.api.assertThrows
import processm.conformance.measures.precision.causalnet.assertDoubleEquals
import processm.core.log.Helpers.logFromString
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.processtree.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PerfectPrecisionTest {

    @Test
    fun `∧(a,×(b,c),⟲(d,e,f)) - available activities`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val c = ProcessTreeActivity("c")
        val d = ProcessTreeActivity("d")
        val e = ProcessTreeActivity("e")
        val f = ProcessTreeActivity("f")
        val loop = RedoLoop(d, e, f)
        val tree = processTree {
            Parallel(
                a,
                Exclusive(b, c),
                loop
            )
        }
        val prec = PerfectPrecision(tree)
        assertEquals(setOf(a, b, c, d), prec.availableActivities(emptyList()))
        assertEquals(setOf(a, b, c, e, f), prec.availableActivities(listOf(d)))
        assertEquals(setOf(a, e, f), prec.availableActivities(listOf(d, c)))
        assertEquals(setOf(a, d), prec.availableActivities(listOf(d, c, e)))
        assertEquals(setOf(d), prec.availableActivities(listOf(d, c, e, a)))
        assertEquals(setOf(e, f), prec.availableActivities(listOf(d, c, e, a, d)))
    }

    @Test
    fun `∧(a,×(b,c),⟲(d,e,f)) - precision`() {
        val model = ProcessTree.parse("∧(a,×(b,c),⟲(d,e,f))")
        val prec = PerfectPrecision(model)
        val log = logFromString(
            """
            a b d
            a b d e d
            a b d f d
        """.trimIndent()
        )
        /**
         * #occurrences | prefix    | possible | observed
         * --------------------------------------------
         * 3            | (empty)   | a b c d  | a
         * 3            | a         | b c d    | b
         * 3            | a b       | d        | d
         * 2            | a b d     | e f      | e f
         * 1            | a b d e   | d        | d
         * 1            | a b d f   | d        | d
         */
        assertDoubleEquals(
            (3 * 1.0 / 4 + 3 * 1.0 / 3 + 3 * 1.0 + 2 * 1.0 + 1 * 1.0 + 1 * 1.0) / (3 + 3 + 3 + 2 + 1 + 1),
            prec(log),
            0.001
        )
    }


    @Test
    fun `fail on cnet`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")

        val diamond1 = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertThrows<IllegalArgumentException> { PerfectPrecision(diamond1) }
    }

}