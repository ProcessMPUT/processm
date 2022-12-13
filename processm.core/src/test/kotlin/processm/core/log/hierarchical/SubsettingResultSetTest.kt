package processm.core.log.hierarchical

import io.mockk.every
import io.mockk.mockk
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubsettingResultSetTest {

    private fun List<Int>.toResultSet(): ResultSet =
        mockk() {
            val data = this@toResultSet.withIndex().toList()
            var pos = -1
            every { next() } answers { ++pos < data.size }
            every { getLong(1) } answers { data[pos].index.toLong() }
            every { getLong(2) } answers { data[pos].value.toLong() }
            every { isBeforeFirst } answers { pos < 0 }
            every { isAfterLast } answers { pos >= data.size }
        }

    @Test
    fun `two pairs`() {
        val base = listOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4).toResultSet()
        with(SubsettingResultSet(base, listOf(1, 2), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(0, 1, 2, 3, 4, 5), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
        with(SubsettingResultSet(base, listOf(3, 4), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(6, 7, 8, 9, 10, 11), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
    }

    @Test
    fun `two pairs with skips`() {
        val base = listOf(1, 1, 1, 3, 3, 3, 5, 5, 5, 7, 7, 7).toResultSet()
        with(SubsettingResultSet(base, listOf(1, 2, 3), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(0, 1, 2, 3, 4, 5), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
        with(SubsettingResultSet(base, listOf(4, 5, 6, 7), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(6, 7, 8, 9, 10, 11), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
    }

    @Test
    fun `two pairs with skip and non-continous ids`() {
        val base = listOf(1, 1, 1, 3, 3, 3, 5, 5, 5, 7, 7, 7).toResultSet()
        with(SubsettingResultSet(base, listOf(1, 2, 3), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(0, 1, 2, 3, 4, 5), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
        with(SubsettingResultSet(base, listOf(5, 6, 7), 2)) {
            assertTrue { isBeforeFirst }
            assertFalse { isAfterLast }
            val actual = ArrayList<Int>()
            while (next())
                actual.add(getLong(1).toInt())
            assertEquals(listOf(6, 7, 8, 9, 10, 11), actual)
            assertFalse { isBeforeFirst }
            assertTrue { isAfterLast }
        }
    }

}