package processm.core.helpers

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SequenceWithMemoryTest {

    @Test
    fun `finite sequence`() {
        var ctr = 0
        val seq = SequenceWithMemory(sequenceOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
            .map { i ->
                ctr += 1
                i
            })
        assertTrue { seq.contains(3) }
        assertTrue { seq.contains(7) }
        assertTrue { seq.contains(5) }
        assertEquals(7, ctr)
    }

    @Test
    fun `infinite sequence`() {
        var ctr = 0
        val seq = generateSequence(0) {
            ctr += 1
            it + 1
        }.withMemory()
        assertTrue { seq.contains(3) }
        assertEquals(3, ctr)
        assertTrue { seq.contains(7) }
        assertEquals(7, ctr)
        assertTrue { seq.contains(5) }
        assertEquals(7, ctr)
        assertTrue { seq.contains(123) }
        assertEquals(123, ctr)
        assertTrue { seq.map { it - 1 }.contains(7) }
        assertEquals(123, ctr)
        assertTrue { seq.map { it - 1 }.contains(255) }
        assertEquals(256, ctr)
    }

    @Test
    fun iterator() {
        val seq = sequenceOf(1, 2, 3, 4, 5).withMemory()
        val i = seq.iterator()
        val target = ArrayList<Int>()
        while (i.hasNext())
            target.add(i.next())
        assertEquals(listOf(1, 2, 3, 4, 5), target)
    }

    @Test
    fun `isEmpty for the empty sequence`() {
        val seq = emptySequence<Any>().asList()
        assertTrue(seq.isEmpty())
        assertEquals(0, seq.size)
    }

    @Test
    fun `isEmpty for a non-empty sequence`() {
        val seq = sequenceOf<Int>(1, 2, 3, 4).asList()
        assertFalse(seq.isEmpty())
        assertEquals(4, seq.size)
    }

    @Test
    fun `get cached item`() {
        val seq = sequenceOf<Int>(1, 2, 3, 4).asList()
        assertEquals(2, seq[1])
        assertEquals(1, seq[0]) // cached
        assertEquals(2, seq[1]) // cached
        assertEquals(3, seq[2])
        assertEquals(4, seq[3])
        assertEquals(4, seq[3]) // cached
    }

    @Test
    fun `throw IndexOutOfBoundsException for index exceeding size`() {
        val seq = sequenceOf<Int>(1, 2, 3, 4).asList()
        assertThrows<IndexOutOfBoundsException> { seq[4] }
        assertThrows<IndexOutOfBoundsException> { seq[4] } // cached
    }
}
