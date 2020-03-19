package processm.core.helpers

import kotlin.test.*

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
}