package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class HierarchicalIterableTest {

    @Test
    fun test() {
        val commonPrefix = mutableListOf(1, 2, 3)
        val a = HierarchicalIterable(commonPrefix, 4)
        assertEquals(listOf(1, 2, 3, 4), a.toList())
        assertEquals(0, a.depth)
        val b = HierarchicalIterable(a, 5)
        assertEquals(listOf(1, 2, 3, 4, 5), b.toList())
        assertEquals(1, b.depth)
        val c = HierarchicalIterable(b, 6, maxDepth = 2)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), c.toList())
        assertEquals(2, c.depth)
        val d = HierarchicalIterable(b, 7)
        assertEquals(listOf(1, 2, 3, 4, 5, 7), d.toList())
        assertEquals(2, d.depth)
        commonPrefix[1] = 9
        assertEquals(listOf(1, 9, 3, 4), a.toList())
        assertEquals(listOf(1, 9, 3, 4, 5), b.toList())
        assertEquals(listOf(1, 2, 3, 4, 5, 6), c.toList())
        assertEquals(listOf(1, 9, 3, 4, 5, 7), d.toList())
        val e = HierarchicalIterable(c, 7)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), e.toList())
    }
}