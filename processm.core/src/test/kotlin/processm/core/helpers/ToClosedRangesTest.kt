package processm.core.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToClosedRangesTest {

    @Test
    fun single() {
        val ranges = listOf(1L, 2, 3, 4, 5).toClosedRanges().toList()
        assertEquals(listOf(1L..5L), ranges)
    }

    @Test
    fun reversed() {
        val ranges = listOf(5L, 4L, 3L, 2L, 1L).toClosedRanges().toList()
        assertEquals(listOf(1L..5L), ranges)
    }

    @Test
    fun `two pairs`() {
        val ranges = listOf(1L, 2, 4, 5).toClosedRanges().toList()
        assertEquals(listOf(1L..2L, 4L..5L), ranges)
    }

    @Test
    fun `three + one`() {
        val ranges = listOf(1L, 2, 3, 5).toClosedRanges().toList()
        assertEquals(listOf(1L..3L, 5L..5L), ranges)
    }

    @Test
    fun one() {
        val ranges = listOf(5L).toClosedRanges().toList()
        assertEquals(listOf(5L..5L), ranges)
    }

    @Test
    fun empty() {
        val ranges = emptyList<Long>().toClosedRanges().toList()
        assertTrue { ranges.isEmpty() }
    }
}