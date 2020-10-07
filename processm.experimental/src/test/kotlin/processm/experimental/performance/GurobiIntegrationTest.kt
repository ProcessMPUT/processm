package processm.experimental.performance

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.Test
import kotlin.test.assertEquals

class GurobiIntegrationTest {

    @Test
    fun cartesianProductTest() {
        val a = listOf('a', 'b', 'c')
        val b = listOf(1, 2)
        val c = listOf('w', 'x', 'y', 'z')
        assertEquals(a.size, cartesianProduct(a).count())
        assertEquals(a.size * b.size, cartesianProduct(a, b).count())
        assertEquals(a.size * b.size * c.size, cartesianProduct(a, b, c).count())
    }

    @Test
    fun `blah`() {
        /*
        val a1 = Node("a1")
        val a2 = Node("a2")
        val a3 = Node("a3")
        val b = Node("b")
        val c1 = Node("c1")
        val c2 = Node("c2")
        val c3 = Node("c3")
        val model = causalnet {
            start splits a1 + a2 + a3
            a1 splits b
            a2 splits b
            a3 splits b
            b splits c1 or c2 or c3
            c1 splits end
            c2 splits end
            c3 splits end
            start joins a1
            start joins a2
            start joins a3
            a1 or a2 or a3 join b
            b joins c1
            b joins c2
            b joins c3
            c1 + c2 + c3 join end
        }
         */
        val a =Node("a")
        val b=Node("b")
        val c=Node("c")
        val d=Node("d")
        val e=Node("e")
        val f=Node("f")
        val model = causalnet {
            start splits a + b or a + c or b + c
            a splits d or e
            b splits d or f
            c splits e or f
            d splits end
            e splits end
            f splits end
            start joins a
            start joins b
            start joins c
            a + b join d
            a + c join e
            b + c join f
            d or e or f join end
        }
        /*
        val model = causalnet {
            start = a
            end = d
            a splits b or c or b+c
            b splits d
            c splits d
            a joins b
            a joins c
            b or c or b+c join d
        }
         */
        GurobiModel(model, 10, 1).solve(listOf(
            listOf(model.start, a),
            listOf(model.start, b),
                    listOf(model.start, f)
        )
        )
    }
}