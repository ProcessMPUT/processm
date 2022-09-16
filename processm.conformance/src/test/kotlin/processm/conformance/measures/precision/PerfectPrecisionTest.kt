package processm.conformance.measures.precision

import processm.core.helpers.Trie
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.logFromString
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.commons.Activity
import processm.core.models.processtree.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        val trie = Trie<Activity, AbstractPrecision.PrecisionData> { AbstractPrecision.PrecisionData(0, 0, 0) }
        trie.getOrPut(listOf(d, c, e, a, d))
        val prec = PerfectPrecision(tree)
        prec.availableActivities(trie)
        assertEquals(4, trie.getOrPut(emptyList()).value.available)
        assertEquals(5, trie.getOrPut(listOf(d)).value.available)
        assertEquals(3, trie.getOrPut(listOf(d, c)).value.available)
        assertEquals(2, trie.getOrPut(listOf(d, c, e)).value.available)
        assertEquals(1, trie.getOrPut(listOf(d, c, e, a)).value.available)
        assertEquals(2, trie.getOrPut(listOf(d, c, e, a, d)).value.available)
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
        assertFailsWith<IllegalArgumentException> { PerfectPrecision(diamond1) }
    }

}
