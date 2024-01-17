package processm.core.verifiers.causalnet

import processm.core.helpers.mapToSet
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.*

class FromModelToLogAndBackAgain {
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

    private fun str(inp: Set<List<Node>>): String {
        return inp.map { seq -> seq.map { it.activity } }.toString()
    }

    fun test(model: CausalNet, expected: Set<List<Node>>) {
        val v = CausalNetVerifierImpl(model)
        assertEquals(
            v.validSequences.mapToSet { seq -> seq.map { it.a } },
            expected
        )
        assertEquals(
            v.validLoopFreeSequences.mapToSet { seq -> seq.map { it.a } },
            expected
        )
        assertFalse(v.hasDeadParts)
        assertTrue(v.isSound)
    }

    @Test
    fun `hell of long-term dependencies`() {
        test(
            causalnet {
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
            },
            setOf(
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d, e),
                listOf(a, b2, b1, c, d, e)
            )
        )
    }

    @Test
    fun `reversed hell of long-term dependencies`() {
        test(
            causalnet {
                start = a
                end = e
                a splits c + d1 or b or c + d2
                b splits d1 + c + d2
                c splits d1 or d2 or d1 + d2
                d1 splits e
                d2 splits e
                a joins b
                a or b join c
                b + c or a + c join d1
                b + c or a + c join d2
                d1 or d2 or d1 + d2 join e
            },
            setOf(
                listOf(a, c, d1, e),
                listOf(a, c, d2, e),
                listOf(a, b, c, d1, d2, e),
                listOf(a, b, c, d2, d1, e)
            )
        )
    }

    /**
     * Intended sequences:
     * a b1 c e
     * a b2 c e
     * a (b1 || b2) c (d1 || d2) e (where || denotes parallel execution, i.e., arbitrary order)
     * a c d1 e
     * a c d2 e
     */
    @Test
    fun `treachery 9th circle of hell`() {
        test(
            causalnet {
                start = a
                end = e
                a splits b1 + e or b2 + e or c + d1 + d2 + b1 + b2 or c + d1 + e or c + d2 + e
                b1 splits c
                b2 splits c
                c splits d1 or e or d2 or d1 + d2
                d1 splits e
                d2 splits e
                a joins b1
                a joins b2
                a or b1 or b2 or a + b1 + b2 join c
                a + c join d1
                a + c join d2
                a + c or d1 + d2 or a + d1 or a + d2 join e
            },
            setOf(
                listOf(a, b1, c, e),
                listOf(a, b2, c, e),
                listOf(a, b1, b2, c, d1, d2, e),
                listOf(a, b1, b2, c, d2, d1, e),
                listOf(a, b2, b1, c, d1, d2, e),
                listOf(a, b2, b1, c, d2, d1, e),
                listOf(a, c, d1, e),
                listOf(a, c, d2, e)
            )
        )
    }

    /**
     * If first X bs are executed, then exactly X ds are executed.
     */
    @Test
    fun `sequential counting`() {
        test(
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
            },
            setOf(
                listOf(a, b1, c, d1, e),
                listOf(a, b1, b2, c, d1, d2, e),
                listOf(a, b1, b2, b3, c, d1, d2, d3, e)
            )
        )
    }

    /**
    Any non-zero number X of bs may be executed in parallel, but then dX must be executed (e.g., if exactly 2 (any) bs were executed, then d2 must be executed)
     */
    @Test
    fun `number indicator`() {
        test(
            causalnet {
                start = a
                end = e
                a splits b1 + d1 or b2 + d1 or b3 + d1
                a splits b1 + b2 + d2 or b1 + b3 + d2 or b2 + b3 + d2
                a splits b1 + b2 + b3 + d3
                b1 splits c + d1 or c + d2 or c + d3
                b2 splits c + d1 or c + d2 or c + d3
                b3 splits c + d1 or c + d2 or c + d3
                c splits d1 or d2 or d3
                d1 splits e
                d2 splits e
                d3 splits e
                a joins b1
                a joins b2
                a joins b3
                b1 or b2 or b3 or b1 + b2 or b1 + b3 or b2 + b3 or b1 + b2 + b3 join c
                a + b1 + c or a + b2 + c or a + b3 + c join d1
                a + b1 + b2 + c or a + b1 + b3 + c or a + b2 + b3 + c join d2
                a + b1 + b2 + b3 + c join d3
                d1 or d2 or d3 join e
            },
            setOf(
                listOf(a, b1, c, d1, e),
                listOf(a, b2, c, d1, e),
                listOf(a, b3, c, d1, e),
                listOf(a, b1, b2, c, d2, e),
                listOf(a, b1, b3, c, d2, e),
                listOf(a, b2, b1, c, d2, e),
                listOf(a, b2, b3, c, d2, e),
                listOf(a, b3, b1, c, d2, e),
                listOf(a, b3, b2, c, d2, e),
                listOf(a, b1, b2, b3, c, d3, e),
                listOf(a, b1, b3, b2, c, d3, e),
                listOf(a, b2, b1, b3, c, d3, e),
                listOf(a, b2, b3, b1, c, d3, e),
                listOf(a, b3, b1, b2, c, d3, e),
                listOf(a, b3, b2, b1, c, d3, e)
            )
        )
    }

    /**
     * First N bs is executed in parallel, then first N ds is to be executed in parallel.
     */
    @Test
    fun `first n in parallel`() {
        test(
            causalnet {
                start = a
                end = e
                a splits b1 or b1 + b2 or b1 + b2 + b3
                b1 splits c + d1
                b2 splits c + d2
                b3 splits c + d3
                c splits d1 or d1 + d2 or d1 + d2 + d3
                d1 splits e
                d2 splits e
                d3 splits e
                a joins b1
                a joins b2
                a joins b3
                b1 or b1 + b2 or b1 + b2 + b3 join c
                b1 + c join d1
                b2 + c join d2
                b3 + c join d3
                d1 or d1 + d2 or d1 + d2 + d3 join e
            }, setOf(
                listOf(a, b1, c, d1, e),
                listOf(a, b1, b2, c, d1, d2, e),
                listOf(a, b2, b1, c, d1, d2, e),
                listOf(a, b1, b2, c, d2, d1, e),
                listOf(a, b2, b1, c, d2, d1, e),

                listOf(a, b1, b2, b3, c, d1, d2, d3, e),
                listOf(a, b1, b2, b3, c, d1, d3, d2, e),
                listOf(a, b1, b2, b3, c, d2, d1, d3, e),
                listOf(a, b1, b2, b3, c, d2, d3, d1, e),
                listOf(a, b1, b2, b3, c, d3, d2, d1, e),
                listOf(a, b1, b2, b3, c, d3, d1, d2, e),

                listOf(a, b1, b3, b2, c, d1, d2, d3, e),
                listOf(a, b1, b3, b2, c, d1, d3, d2, e),
                listOf(a, b1, b3, b2, c, d2, d1, d3, e),
                listOf(a, b1, b3, b2, c, d2, d3, d1, e),
                listOf(a, b1, b3, b2, c, d3, d2, d1, e),
                listOf(a, b1, b3, b2, c, d3, d1, d2, e),

                listOf(a, b2, b1, b3, c, d1, d2, d3, e),
                listOf(a, b2, b1, b3, c, d1, d3, d2, e),
                listOf(a, b2, b1, b3, c, d2, d1, d3, e),
                listOf(a, b2, b1, b3, c, d2, d3, d1, e),
                listOf(a, b2, b1, b3, c, d3, d2, d1, e),
                listOf(a, b2, b1, b3, c, d3, d1, d2, e),

                listOf(a, b2, b3, b1, c, d1, d2, d3, e),
                listOf(a, b2, b3, b1, c, d1, d3, d2, e),
                listOf(a, b2, b3, b1, c, d2, d1, d3, e),
                listOf(a, b2, b3, b1, c, d2, d3, d1, e),
                listOf(a, b2, b3, b1, c, d3, d2, d1, e),
                listOf(a, b2, b3, b1, c, d3, d1, d2, e),

                listOf(a, b3, b2, b1, c, d1, d2, d3, e),
                listOf(a, b3, b2, b1, c, d1, d3, d2, e),
                listOf(a, b3, b2, b1, c, d2, d1, d3, e),
                listOf(a, b3, b2, b1, c, d2, d3, d1, e),
                listOf(a, b3, b2, b1, c, d3, d2, d1, e),
                listOf(a, b3, b2, b1, c, d3, d1, d2, e),

                listOf(a, b3, b1, b2, c, d1, d2, d3, e),
                listOf(a, b3, b1, b2, c, d1, d3, d2, e),
                listOf(a, b3, b1, b2, c, d2, d1, d3, e),
                listOf(a, b3, b1, b2, c, d2, d3, d1, e),
                listOf(a, b3, b1, b2, c, d3, d2, d1, e),
                listOf(a, b3, b1, b2, c, d3, d1, d2, e)
            )
        )
    }

    /**
     * Any non-zero number N of bs may be executed, and then first N ds must be executed.
     * Both bs and ds are executed in parallel.
     * For example, a valid sequence is `a b3 b2 c d1 d2 e`
     */
    @Test
    fun `parallel counting`() {
        assertEquals(CausalNetVerifierImpl(causalnet {
            start = a
            end = e
            a splits b1 or b2 or b3 or b1 + b2 + d2 or b1 + b3 + d2 or b2 + b3 + d2 or b1 + b2 + b3 + d2 + d3
            b1 splits c
            b2 splits c
            b3 splits c
            c splits d1 or d1 + d2 or d1 + d2 + d3
            d1 splits e
            d2 splits e
            d3 splits e
            a joins b1
            a joins b2
            a joins b3
            b1 or b2 or b3 or b1 + b2 or b2 + b3 or b1 + b3 or b1 + b2 + b3 join c
            c joins d1
            a + c join d2
            a + c join d3
            d1 or d1 + d2 or d1 + d2 + d3 join e
        }).validSequences.count(), 3 * 1 + 3 * 2 * 2 + 3 * 2 * 3 * 2)
    }

    @Ignore("Too expensive")
    @Test
    fun `binary to unary decoder`() {
        test(
            causalnet {
                start = a
                end = e
                a splits c or b0 or b1 or b1 + b0 or b2 or b2 + b0 or b2 + b1 or b2 + b1 + b0
                b0 splits c + d1 or c + d3 or c + d5 or c + d7
                b1 splits c + d2 or c + d3 or c + d6 or c + d7
                b2 splits c + d4 or c + d5 or c + d6 or c + d7
                c splits d0 or d1 or d2 or d3 or d4 or d5 or d6 or d7
                d0 splits e
                d1 splits e
                d2 splits e
                d3 splits e
                d4 splits e
                d5 splits e
                d6 splits e
                d7 splits e
                a joins b0
                a joins b1
                a joins b2
                a or b0 or b1 or b1 + b0 or b2 or b2 + b0 or b2 + b1 or b2 + b1 + b0 join c
                c joins d0
                b0 + c join d1
                b1 + c join d2
                b1 + b0 + c join d3
                b2 + c join d4
                b2 + b0 + c join d5
                b2 + b1 + c join d6
                b2 + b1 + b0 + c join d7
                d0 or d1 or d2 or d3 or d4 or d5 or d6 or d7 join e
            },
            setOf(
                listOf(a, c, d0, e),
                listOf(a, b0, c, d1, e),
                listOf(a, b1, c, d2, e),
                listOf(a, b0, b1, c, d3, e),
                listOf(a, b1, b0, c, d3, e),
                listOf(a, b2, c, d4, e),
                listOf(a, b2, b0, c, d5, e),
                listOf(a, b2, b1, c, d6, e),
                listOf(a, b0, b2, c, d5, e),
                listOf(a, b1, b2, c, d6, e),
                listOf(a, b2, b1, b0, c, d7, e),
                listOf(a, b2, b0, b1, c, d7, e),
                listOf(a, b1, b2, b0, c, d7, e),
                listOf(a, b1, b0, b2, c, d7, e),
                listOf(a, b0, b1, b2, c, d7, e),
                listOf(a, b0, b2, b1, c, d7, e)
            )
        )
    }

    @Test
    fun `flexible fuzzy miner long-term dependency example`() {
        test(
            causalnet {
                start = a
                end = e
                a splits b1 or b2
                b1 splits c + d1
                b2 splits c + d2
                c splits d1 or d2
                d1 splits e
                d2 splits e
                a joins b1
                a joins b2
                b1 or b2 join c
                b1 + c join d1
                b2 + c join d2
                d1 or d2 join e
            },
            setOf(
                listOf(a, b1, c, d1, e),
                listOf(a, b2, c, d2, e)
            )
        )
    }
}