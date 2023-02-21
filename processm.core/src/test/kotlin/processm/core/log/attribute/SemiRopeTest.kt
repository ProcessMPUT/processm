package processm.core.log.attribute

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemiRopeTest {

    val r1 = SemiRope("ab", "cde")
    val r2 = SemiRope(SemiRope("ab", "cde"), "fghi")

    @Test
    fun length() {
        assertEquals(5, r1.length)
        assertEquals(9, r2.length)
    }

    @Test
    fun `compareTo String`() {
        assertEquals(0, r1.compareTo("abcde"))
        assertEquals(0, r2.compareTo("abcdefghi"))
    }

    @Test
    fun subSequence() {
        val expected = "abcdefghi"
        for (start in 0 until expected.length - 1) {
            for (end in start + 1 until expected.length) {
                assertEquals(expected.subSequence(start, end), r2.subSequence(start, end).toString())
            }
        }
    }

    @Test
    fun get() {
        val expected = "abcdefghi"
        for (i in expected.indices)
            assertEquals(expected[i], r2[i])
    }

    @Test
    fun `compareTo SemiRope`() {
        val ab_cde = SemiRope("ab", "cde")
        val ab_cde_fgh = SemiRope(ab_cde, "fgh")
        val ab_cde_fghi = SemiRope(ab_cde, "fghi")
        val ab_cde_fghj = SemiRope(ab_cde, "fghj")
        assertTrue { ab_cde < ab_cde_fgh }
        assertTrue { ab_cde_fgh < ab_cde_fghi }
        assertTrue { ab_cde_fghi < ab_cde_fghj }
    }
}