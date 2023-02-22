package processm.core.log.attribute

import kotlin.math.sign
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

    private fun ropify(s: String): Sequence<SemiRope> = sequence {
        yield(SemiRope("", s))
        if (s.length > 1) {
            for (i in 1 until s.length) {
                val leftString = s.substring(0, i)
                val right = s.substring(i)
                yieldAll(ropify(leftString).map { left -> SemiRope(left, right) })
            }
        }
    }

    private fun CharSequence.toDebugString(): String =
        if (this is SemiRope) "${left.toDebugString()} -> $right" else toString()

    @Test
    fun `test ropify`() {
        val s = "abcd"
        val ropes = ropify(s).toList()
        // there can be a cut between any two characters
        assertEquals((1 shl (s.length - 1)), ropes.size)
        for (rope in ropes)
            assertEquals(s, rope.toString())
    }

    @Test
    fun compareTo() {
        val strings = listOf("a", "aa", "abb", "abcc", "abcd", "abd", "ac", "b").sorted()
        repeat(1) {
            for (s1 in strings) {
                for (s2 in strings) {
                    for (rope1 in ropify(s1)) {
                        for (rope2 in ropify(s2)) {
                            assertEquals(rope1.compareTo(rope2).sign, s1.compareTo(s2).sign)
                        }
                    }
                }
            }
        }
    }
}