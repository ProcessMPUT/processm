package processm.experimental.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class UInt128Test {

    @Test
    fun `shl test 1`() {
        val a = UInt128(0x1U)
        a.shl(4)
        assertEquals(UInt128(0x10U), a)
    }

    @Test
    fun `shl test 2`() {
        val a = UInt128(0x1U)
        a.shl(63)
        assertEquals(UInt128(0x8000000000000000UL), a)
    }

    @Test
    fun `shl test 3`() {
        val a = UInt128(0x1U)
        a.shl(64)
        assertEquals(UInt128(0x1UL, 0UL), a)
    }

    @Test
    fun `shl test 4`() {
        val a = UInt128(0x7U, 0x1U)
        a.shl(68)
        assertEquals(UInt128(0x10UL, 0UL), a)
    }
}