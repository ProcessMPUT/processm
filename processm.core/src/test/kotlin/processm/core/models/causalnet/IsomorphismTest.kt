package processm.core.models.causalnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsomorphismTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val a1 = Node("a1")
    private val b1 = Node("b1")
    private val c1 = Node("c1")
    private val d1 = Node("d1")
    private val e1 = Node("e1")

    @Test
    fun `xor vs or`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        val right = causalnet {
            start = a
            end = d
            a splits b or c
            b splits d
            c splits d
            a joins b
            a joins c
            b or c join d
        }
        assertNull(left.isomorphic(right, emptyMap()))
        assertNull(right.isomorphic(left, emptyMap()))
    }

    @Test
    fun `misleading initial solution`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertNull(left.isomorphic(left, mapOf(a to d, b to b)))
    }

    @Test
    fun `very misleading initial solution`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertNull(left.isomorphic(left, mapOf(a to b, b to d, c to a, d to c)))
    }

    @Test
    fun `symmetric without help`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertEquals(mapOf(a to a, b to b, c to c, d to d), left.isomorphic(left, emptyMap()))
    }

    @Test
    fun `symmetric with help`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        assertEquals(mapOf(a to a, b to b, c to c, d to d), left.isomorphic(left, mapOf(b to b)))
    }

    @Test
    fun `renamed symmetric with help`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        val map = mapOf(a to a1, b to b1, c to c1, d to d1)
        val right = MutableCausalNet(start = a1, end = d1)
        right.copyFrom(left) { map.getValue(it) }
        assertEquals(map, left.isomorphic(right, mapOf(b to b1)))
    }

    @Test
    fun `asymmetric binding`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c or c
            b splits d
            c splits d
            a joins b
            a joins c
            c or b + c join d
        }
        val map = mapOf(a to a1, b to b1, c to c1, d to d1)
        val right = MutableCausalNet(start = a1, end = d1)
        right.copyFrom(left) { map.getValue(it) }
        assertEquals(map, left.isomorphic(right, emptyMap()))
    }

    @Test
    fun `asymmetric dependency`() {
        val left = causalnet {
            start = a
            end = d
            a splits b + c
            b splits c + d
            c splits d
            a joins b
            a + b join c
            b + c join d
        }
        val map = mapOf(a to a1, b to b1, c to c1, d to d1)
        val right = MutableCausalNet(start = a1, end = d1)
        right.copyFrom(left) { map.getValue(it) }
        assertEquals(map, left.isomorphic(right, emptyMap()))
    }
}