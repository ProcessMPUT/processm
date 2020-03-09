package processm.core.models.causalnet.verifier

import org.junit.jupiter.api.Test
import processm.core.models.causalnet.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A hell of long term dependencies. There are four intended sequences:
 * a b1 c e
 * a b2 c e
 * a b1 b2 c d e
 * a b2 b1 c d e
 *
 * The most important elements are:
 * Join b1, b2, c -> d: to ensure that both b1 and b2 were executed and to ensure that d is executed after c
 * Joins b1, c -> e/b2, c -> e/d -> e: to ensure that d is not skipped if both b1 and b2 were executed
 */
class HellOfLongTermDependencies {
    val a = Node("a")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
    val reference = causalnet {
        start = a
        end = e
        a splits b1 or b2 or b1 + b2
        b1 splits c + e or c + d
        b2 splits c + e or c + d
        c splits d or e
        d splits e
        a joins b1
        a joins b2
        b1 or b2 or b1 + b2 join c
        b1 + b2 + c join d
        c + b1 or c + b2 or d join e
    }

    @Test
    fun test() {
        val v = Verifier(reference)
        assertEquals(
            setOf(
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d, e),
                listOf(a, b2, b1, c, d, e)
            ),
            v.validSequences.map { seq -> seq.map { ab -> ab.a } }.toSet()
        )
        assertFalse(v.hasDeadParts)
        assertTrue(v.isSound)
    }
}