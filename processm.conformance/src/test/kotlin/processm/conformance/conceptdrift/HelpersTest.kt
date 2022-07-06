package processm.conformance.conceptdrift

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HelpersTest {


    @Test
    fun cvFoldsEven() {
        val folds = cvFolds(3, 33).toList()
        assertEquals(3, folds.size)
        assertEquals(0 until 11, folds[0])
        assertEquals(11 until 22, folds[1])
        assertEquals(22 until 33, folds[2])
    }

    @Test
    fun cvFoldsUneven() {
        val folds = cvFolds(3, 35).toList()
        assertEquals(3, folds.size)
        assertEquals(0 until 12, folds[0])
        assertEquals(12 until 24, folds[1])
        assertEquals(24 until 35, folds[2])
    }

    @Test
    fun `allExcept - middle`() {
        val list = (0 until 10).toList()
        assertEquals(listOf(0, 1, 7, 8, 9), list.allExcept(2 until 7))
    }

    @Test
    fun `allExcept - left`() {
        val list = (0 until 10).toList()
        assertEquals(listOf(5, 6, 7, 8, 9), list.allExcept(0 until 5))
    }

    @Test
    fun `allExcept - right`() {
        val list = (0 until 10).toList()
        assertEquals(listOf(0, 1, 2, 3, 4), list.allExcept(5 until 10))
    }

    @Test
    fun transpose() {
        val input = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6)
        )
        val output = input.transpose()
        val expected = listOf(
            listOf(1, 4),
            listOf(2, 5),
            listOf(3, 6)
        )
        assertContentEquals(expected, output)
    }
}