package processm.experimental.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class UInt128Test {

    @Test
    fun `shl test`() {
        val a = UInt128(0x1U)
        assertEquals(UInt128(0x10U), a shl 4)
        assertEquals(UInt128(0x8000000000000000UL), a shl 63)
        assertEquals(UInt128(0x1UL, 0UL), a shl 64)
    }

    @Test
    fun `shl test 2`() {
        val a = UInt128(0x7U, 0x1U)
        assertEquals(UInt128(0x10UL, 0UL), a shl 68)
    }
}