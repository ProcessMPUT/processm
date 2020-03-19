package processm.core.verifiers

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.*

class CausalNetVerifierTest {

    val a = Node("a")
    val b = Node("b")
    val b0 = Node("b0")
    val b1 = Node("b1")
    val b2 = Node("b2")
    val b3 = Node("b3")
    val c = Node("c")
    val d = Node("d")
    val d0 = Node("d0")
    val d1 = Node("d1")
    val d2 = Node("d2")
    val d3 = Node("d3")
    val d4 = Node("d4")
    val d5 = Node("d5")
    val d6 = Node("d6")
    val d7 = Node("d7")
    val e = Node("e")

    /**
     * If first X bs are executed, then exactly X ds are executed.
     */
    @Test
    fun `sequential counting`() {
        val model1 =
            causalnet {
                start = a
                end = e
                a splits b1
                b1 splits b2 or c + e
                b2 splits b3 or c + e
                b3 splits c + e
                c splits d1
                d1 splits d2 or e
                d2 splits d3 or e
                d3 splits e
                a joins b1
                b1 joins b2
                b2 joins b3
                b1 or b2 or b3 join c
                c joins d1
                d1 joins d2
                d2 joins d3
                b1 + d1 or b2 + d2 or b3 + d3 join e
            }
        val model2 = causalnet {
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
        val v = CausalNetVerifier()
        val vr1 = v.verify(model1)
        val vr2 = v.verify(model2)
        assertNotEquals(vr1, vr2)
        assertEquals(
            vr2.validSequences.map { seq -> seq.map { ab -> ab.a } }.toSet(), setOf(
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d, e),
                listOf(a, b2, b1, c, d, e)
            )
        )
        assertTrue { vr1.isSound }
        assertTrue { vr2.isSound }
    }

    @Test
    fun unsound() {
        val vr = CausalNetVerifier().verify(causalnet {

        })
        assertFalse { vr.isSound }
        assertFalse { vr.noDeadParts }
    }
}