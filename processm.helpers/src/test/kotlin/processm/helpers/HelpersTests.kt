package processm.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HelpersTests {
    @Test
    fun `intersect - empty`() {
        assertTrue { intersect(emptyList<Set<Int>>()).isEmpty() }
    }

    @Test
    fun `intersect - one set`() {
        val set = setOf(1, 2, 3)
        assertSame(set, intersect(listOf(set)))
    }

    @Test
    fun `intersect - three sets with common elements`() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(1, 2, 4)
        val set3 = setOf(2, 3, 4)
        assertEquals(setOf(2), intersect(listOf(set1, set2, set3)))
    }

    @Test
    fun `intersect - three disjoint sets`() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(2, 3, 4)
        val set3 = setOf(4, 5, 6)
        assertEquals(emptySet(), intersect(listOf(set1, set2, set3)))
    }
}
