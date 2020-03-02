package processm.core

import kotlin.test.Test
import kotlin.test.assertEquals

internal class BrandTest {
    @Test
    fun `ProcessM as brand name`() {
        assertEquals(Brand.name, "ProcessM")
    }
}