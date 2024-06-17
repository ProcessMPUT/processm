package processm.core.models.causalnet

import kotlin.test.Test
import kotlin.test.assertEquals

class CausalNetDSLTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")

    @Test
    fun `single join`() {
        val model = causalnet {
            a joins c
        }
        assertEquals(listOf(Join(setOf(Dependency(a, c)))), model.joins[c])
    }

    @Test
    fun `double join`() {
        val model = causalnet {
            a + b join c
        }
        assertEquals(listOf(Join(setOf(Dependency(a, c), Dependency(b, c)))), model.joins[c])
    }


    @Test
    fun `two joins`() {
        val model = causalnet {
            a or b join c
        }
        assertEquals(listOf(Join(setOf(Dependency(a, c))), Join(setOf(Dependency(b, c)))), model.joins[c])
    }

    @Test
    fun `two double joins`() {
        val model = causalnet {
            a + c or b + c join c
        }
        assertEquals(
            listOf(Join(setOf(Dependency(a, c), Dependency(c, c))), Join(setOf(Dependency(b, c), Dependency(c, c)))),
            model.joins[c]
        )
    }

    @Test
    fun `two triple joins`() {
        val model = causalnet {
            a + b + c or b + c + d join c
        }
        assertEquals(
            listOf(
                Join(setOf(Dependency(a, c), Dependency(b, c), Dependency(c, c))),
                Join(setOf(Dependency(b, c), Dependency(c, c), Dependency(d, c)))
            ),
            model.joins[c]
        )
    }

    @Test
    fun `single and double join`() {
        val model = causalnet {
            a or b + c join c
        }
        assertEquals(
            setOf(Join(setOf(Dependency(a, c))), Join(setOf(Dependency(b, c), Dependency(c, c)))),
            model.joins[c]?.toSet()
        )
    }

    @Test
    fun `single and two double joins`() {
        val model = causalnet {
            (a or (b + c or a + b + c)) join c
        }
        assertEquals(
            setOf(
                Join(setOf(Dependency(a, c))), Join(setOf(Dependency(b, c), Dependency(c, c))),
                Join(setOf(Dependency(a, c), Dependency(b, c), Dependency(c, c)))
            ),
            model.joins[c]?.toSet()
        )
    }


    @Test
    fun `single split`() {
        val model = causalnet {
            a splits c
        }
        assertEquals(listOf(Split(setOf(Dependency(a, c)))), model.splits[a])
    }

    @Test
    fun `double split`() {
        val model = causalnet {
            a splits b + c
        }
        assertEquals(listOf(Split(setOf(Dependency(a, b), Dependency(a, c)))), model.splits[a])
    }


    @Test
    fun `two splits`() {
        val model = causalnet {
            a splits b or c
        }
        assertEquals(listOf(Split(setOf(Dependency(a, b))), Split(setOf(Dependency(a, c)))), model.splits[a])
    }

    @Test
    fun `two double splits`() {
        val model = causalnet {
            a splits a + c or b + c
        }
        assertEquals(
            listOf(Split(setOf(Dependency(a, a), Dependency(a, c))), Split(setOf(Dependency(a, b), Dependency(a, c)))),
            model.splits[a]
        )
    }
}
