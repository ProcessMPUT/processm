package processm.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class TripleSortTest {

    @Test
    fun `1 2 3`() {
        assertEquals(Triple(1, 2, 3), Triple(1, 2, 3).sort())
    }

    @Test
    fun `2 1 3`() {
        assertEquals(Triple(1, 2, 3), Triple(2, 1, 3).sort())
    }

    @Test
    fun `1 3 2`() {
        assertEquals(Triple(1, 2, 3), Triple(1, 3, 2).sort())
    }

    @Test
    fun `2 3 1`() {
        assertEquals(Triple(1, 2, 3), Triple(2, 3, 1).sort())
    }


    @Test
    fun `3 1 2`() {
        assertEquals(Triple(1, 2, 3), Triple(3, 1, 2).sort())
    }

    @Test
    fun `3 2 1`() {
        assertEquals(Triple(1, 2, 3), Triple(3, 2, 1).sort())
    }

}