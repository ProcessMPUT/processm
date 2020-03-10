package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeTest {
    @Test
    fun `Add two nodes with + operator`() {
        val check = Parallel() + Activity("A") + Activity("B")
        val expected = Parallel().also {
            it.childrenInternal.add(Activity("A"))
            it.childrenInternal.add(Activity("B"))
        }

        assertEquals(expected, check)
    }
}