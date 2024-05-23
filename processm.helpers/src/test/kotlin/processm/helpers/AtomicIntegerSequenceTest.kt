package processm.helpers

import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicIntegerSequenceTest {
    @Test
    fun `0 to 0 returns 0 and then throws ArithmeticException`() {
        val sequence = AtomicIntegerSequence(0, 0)
        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())

        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `0 to 0 returns 0 and then throws ArithmeticException when used as iterator`() {
        val sequence = AtomicIntegerSequence(0, 0).iterator()
        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())

        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `0 to 0 returns 0 and then overflows`() {
        val sequence = AtomicIntegerSequence(0, 0, allowOverflow = true)
        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())

        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())
    }

    @Test
    fun `0 to 0 returns 0 and then overflows when used as iterator`() {
        val sequence = AtomicIntegerSequence(0, 0, allowOverflow = true).iterator()
        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())

        assertTrue(sequence.hasNext())
        assertEquals(0, sequence.next())
    }

    @Test
    fun `Int MIN_VALUE to Int MAX_VALUE returns 2^32 values then throws ArithmeticException`() {
        val sequence = AtomicIntegerSequence(Int.MIN_VALUE, Int.MAX_VALUE)
        var expected = Int.MIN_VALUE
        while (expected < Int.MAX_VALUE) {
            // As this loop runs 2**32 - 1 iterations, we speed it up twice by inline verification of assertions:
            if (!sequence.hasNext()) throw AssertionFailedError()
            if (expected++ != sequence.next()) throw AssertionFailedError()
        }

        assertTrue(sequence.hasNext())
        assertEquals(expected, sequence.next())

        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `Int MIN_VALUE to Int MIN_VALUE + 4 returns 5 values then throws ArithmeticException`() {
        val sequence = AtomicIntegerSequence(Int.MIN_VALUE, Int.MIN_VALUE + 4)
        assertEquals(Int.MIN_VALUE, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 1, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 2, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 3, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 4, sequence.next())
        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `Int MIN_VALUE to Int MIN_VALUE + 4 returns 5 values then throws ArithmeticException when used as iterator`() {
        val sequence = AtomicIntegerSequence(Int.MIN_VALUE, Int.MIN_VALUE + 4).iterator()
        assertEquals(Int.MIN_VALUE, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 1, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 2, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 3, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MIN_VALUE + 4, sequence.next())
        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `Int MAX_VALUE - 4 to Int MAX_VALUE returns 5 values then throws ArithmeticException`() {
        val sequence = AtomicIntegerSequence(Int.MAX_VALUE - 4, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE - 4, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 3, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 2, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 1, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE, sequence.next())
        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `Int MAX_VALUE - 4 to Int MAX_VALUE returns 5 values then throws ArithmeticException when used as iterator`() {
        val sequence = AtomicIntegerSequence(Int.MAX_VALUE - 4, Int.MAX_VALUE).iterator()
        assertEquals(Int.MAX_VALUE - 4, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 3, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 2, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE - 1, sequence.next())
        assertTrue(sequence.hasNext())
        assertEquals(Int.MAX_VALUE, sequence.next())
        assertFalse(sequence.hasNext())
        assertThrows<ArithmeticException> { sequence.next() }
    }

    @Test
    fun `10 to 0 throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { AtomicIntegerSequence(10, 0) }
    }
}
