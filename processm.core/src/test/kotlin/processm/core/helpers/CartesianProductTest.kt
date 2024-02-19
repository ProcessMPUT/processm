package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class CartesianProductTest {
    @Test
    fun `Cartesian product of two lists consists of all pairs`() {
        val a = arrayListOf('a', 'b', 'c')
        val b = arrayListOf(1, 2)
        val c = arrayListOf('w', 'x', 'y', 'z')
        assertEquals(a.map { listOf(it) }, listOf(a).cartesianProduct().toList())
        assertEquals(
            listOf(listOf('a', 1), listOf('b', 1), listOf('c', 1), listOf('a', 2), listOf('b', 2), listOf('c', 2)),
            listOf(a, b).cartesianProduct().toList()
        )
        assertEquals(a.size * b.size * c.size, listOf(a, b, c).cartesianProduct().count())
    }

    @Test
    fun `Cartesian product of empty list consists of empty list only`() {
        val emptyProduct = emptyList<ArrayList<Any>>().cartesianProduct().toList()
        assertEquals(1, emptyProduct.size)
        assertEquals(emptyList<Any>(), emptyProduct.first())
    }

    @Test
    fun `Cartesian product of three lists where one is empty is empty`() {
        val a = arrayListOf('a', 'b', 'c')
        val b = arrayListOf(1, 2)
        val c = arrayListOf<Any>()

        assertEquals(0, listOf(a, b, c).cartesianProduct().toList().size)
        assertEquals(0, listOf(a, c, b).cartesianProduct().toList().size)
        assertEquals(0, listOf(c, a, b).cartesianProduct().toList().size)
    }
}
