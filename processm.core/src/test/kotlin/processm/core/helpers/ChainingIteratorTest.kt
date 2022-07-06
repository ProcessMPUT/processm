package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChainingIteratorTest {
    @Test
    fun `two non-empty lists`() {
        val i = chain(listOf(1, 2), listOf(3, 4))
        assertEquals(listOf(1, 2, 3, 4), i.asSequence().toList())
        assertFalse(i.hasNext())
    }

    @Test
    fun `two non-empty lists with an empty list in the middle`() {
        val i = chain(listOf(1, 2), listOf(), listOf(3, 4))
        assertEquals(listOf(1, 2, 3, 4), i.asSequence().toList())
        assertFalse(i.hasNext())
    }

    @Test
    fun `one non-empty lists`() {
        val i = chain(listOf(1, 2))
        assertEquals(listOf(1, 2), i.asSequence().toList())
        assertFalse(i.hasNext())
    }

    @Test
    fun `one empty lists`() {
        val i = chain(emptyList<Int>())
        assertEquals(emptyList(), i.asSequence().toList())
        assertFalse(i.hasNext())
    }

    @Test
    fun `empty chain`() {
        val i = chain(emptyList<Iterable<Int>>())
        assertEquals(emptyList(), i.asSequence().toList())
        assertFalse(i.hasNext())
    }

}