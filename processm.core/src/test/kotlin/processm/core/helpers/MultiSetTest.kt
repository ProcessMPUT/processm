package processm.core.helpers

import kotlin.test.*

class MultiSetTest {

    @Test
    fun test() {
        val ms = MultiSet<Int>()
        assertTrue { ms.isEmpty() }
        assertFalse { ms.contains(1) }
        ms.add(1)
        assertEquals(listOf(1), ms.toList().sorted())
        assertTrue { ms.contains(1) }
        ms.add(1)
        assertEquals(listOf(1, 1), ms.toList().sorted())
        ms.add(2)
        assertEquals(listOf(1, 1, 2), ms.toList().sorted())
        ms.add(2)
        assertEquals(listOf(1, 1, 2, 2), ms.toList().sorted())
        ms.add(1)
        assertEquals(listOf(1, 1, 1, 2, 2), ms.toList().sorted())
        assertTrue { ms.remove(1) }
        assertFalse { ms.remove(777) }
        assertEquals(listOf(1, 1, 2, 2), ms.toList().sorted())
        ms.removeAll(listOf(1))
        assertEquals(listOf(1, 2, 2), ms.toList().sorted())
        ms.removeAll(listOf(1))
        assertEquals(listOf(2, 2), ms.toList().sorted())
        ms.addAll(listOf(4, 4, 5, 5, 5))
        assertEquals(listOf(2, 2, 4, 4, 5, 5, 5), ms.toList().sorted())
        assertTrue { ms.containsAll(listOf(2, 4)) }
        assertTrue { ms.containsAll(listOf(2, 4, 4)) }
        assertFalse { ms.containsAll(listOf(2, 4, 4, 4)) }
        ms.remove(4)
        ms.remove(4)
        assertEquals(listOf(2, 2, 5, 5, 5), ms.toList().sorted())
        assertFalse { ms.isEmpty() }
        ms.clear()
        assertTrue { ms.isEmpty() }
        assertFailsWith<NoSuchElementException> { ms.iterator().next() }
    }

    @Test
    fun `not implemented`() {
        assertFailsWith<NotImplementedError> { MultiSet<Int>().iterator().remove() }
        assertFailsWith<NotImplementedError> { MultiSet<Int>().retainAll(listOf()) }
    }

    @Test
    fun `test equals`() {
        val a = MultiSet<Int>()
        a.add(1)
        val b = MultiSet<Int>()
        b.addAll(listOf(1, 1))
        val c = MultiSet<Int>()
        c.add(1)
        assertTrue { a == c }
        assertFalse { a == b }
        assertFalse { b == c }
        assertTrue { a.hashCode() == c.hashCode() }
        assertFalse { a == null }
    }

    @Test
    fun `copy constructor`() {
        val a = MultiSet<Int>()
        a.addAll(listOf(1, 2, 2, 3, 3, 3))
        val b = MultiSet(a)
        assertEquals(a, b)
    }
}