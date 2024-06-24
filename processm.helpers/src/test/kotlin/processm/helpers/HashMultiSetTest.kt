package processm.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashMultiSetTest {
    @Test
    fun empty() {
        val multiset = HashMultiSet<String>()
        assertTrue { multiset.isEmpty() }
        assertEquals(0, multiset.size)
        assertEquals(0, multiset.uniqueSize)
        assertTrue { multiset.entrySet().none() }
        assertTrue { multiset.uniqueSet().none() }
        assertTrue { multiset.countSet().none() }
    }

    @Test
    fun one() {
        val multiset = HashMultiSet<String>()
        multiset.add("one")

        assertFalse { multiset.isEmpty() }
        assertEquals(1, multiset.size)
        assertEquals(1, multiset.uniqueSize)
        assertEquals(1, multiset.entrySet().count())
        assertEquals("one", multiset.entrySet().first().element)
        assertEquals(1, multiset.entrySet().first().count)
        assertEquals(setOf("one"), multiset.uniqueSet().toSet())
        assertEquals(setOf(1.toByte()), multiset.countSet().toSet())
    }

    @Test
    fun `one one`() {
        val multiset = HashMultiSet<String>()
        multiset.add("one")
        multiset.add("one")

        assertFalse { multiset.isEmpty() }
        assertEquals(2, multiset.size)
        assertEquals(1, multiset.uniqueSize)
        assertEquals(1, multiset.entrySet().count())
        assertEquals("one", multiset.entrySet().first().element)
        assertEquals(2, multiset.entrySet().first().count)
        assertEquals(setOf("one"), multiset.uniqueSet().toSet())
        assertEquals(setOf(2.toByte()), multiset.countSet().toSet())
    }
}
