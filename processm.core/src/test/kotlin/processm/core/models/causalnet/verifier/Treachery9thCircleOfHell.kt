package processm.core.models.causalnet.verifier

import processm.core.models.causalnet.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A showcase of long term dependency between two pairs of nodes.
 * There is exactly one long-term dependency in this model: (b1&b2)->(d1&d2)
 * In order to avoid decomposition, all activities participating in this dependency must be executable separately, otherwise
 * * If b1 must be always executed with b2, (b1/b2) -> (d1&d2) would be enough
 * * If d1 must be always executed with d2, (b1&b2) -> (d1/d2) would be enough
 * * If b1/b2 must be always executed with d1/d2, it would be possible to decompose the dependency into two, e.g., b1->d1 and b2->d2 (or vice-versa)
 */
class Treachery9thCircleOfHell {

    val a = Node("a")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val c = Node("c")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val e = Node("e")

    val model = causalnet {
        start = a
        end = e
        a splits b1 or b2 or b1 + b2 or d1 or d2
        b1 splits c + e or d1 + c + d2
        b2 splits c + e or d1 + c + d2
        d1 splits e
        d2 splits e
        c splits d1 + d2 or e
        a joins b1
        a joins b2
        b1 or b2 or b1 + b2 join c
        a or b1 + b2 + c join d1
        a or b1 + b2 + c join d2
        b1 + c or b2 + c or d1 + d2 or d1 or d2 join e
    }

    @Test
    fun test() {
        val v = Verifier(model)
        assertEquals(
            setOf(
                listOf(a, d1, e),
                listOf(a, d2, e),
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d1, d2, e),
                listOf(a, b1, b2, c, d2, d1, e),
                listOf(a, b2, b1, c, d1, d2, e),
                listOf(a, b2, b1, c, d2, d1, e)
            ), v.validSequences.map { seq -> seq.map { it.a } }.toSet()
        )
        assertFalse { v.hasDeadParts }
        assertTrue { v.isSound }
    }
}