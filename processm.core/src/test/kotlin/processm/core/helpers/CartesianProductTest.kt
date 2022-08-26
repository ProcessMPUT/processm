package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class CartesianProductTest {
    @Test
    fun cartesianProductTest() {
        val a = listOf('a', 'b', 'c')
        val b = listOf(1, 2)
        val c = listOf('w', 'x', 'y', 'z')
        assertEquals(a.map { listOf(it) }, listOf(a).cartesianProduct().toList())
        assertEquals(
            listOf(listOf('a', 1), listOf('b', 1), listOf('c', 1), listOf('a', 2), listOf('b', 2), listOf('c', 2)),
            listOf(a, b).cartesianProduct().toList()
        )
        assertEquals(a.size * b.size * c.size, listOf(a, b, c).cartesianProduct().count())
    }
}