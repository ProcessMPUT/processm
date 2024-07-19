package processm.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StepArgMinTest {

    @Test
    fun `returns the lower threshold if possible`() {
        assertEquals(0.0, stepArgMin(0.0, 1.0) { it })
    }

    @Test
    fun `finds minimum`() {
        val v = stepArgMin(0.0, 1.0) { if (it >= 0.44) it else null }
        assertNotNull(v)
        assertTrue { v in 0.44..0.45 }
    }
}