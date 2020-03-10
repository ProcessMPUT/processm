package processm.core.models.processtree

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeTest {
    @Test
    fun `Add two nodes with + operator`() {
        val expected = processTree {
            Parallel(
                Activity("A"),
                Activity("B")
            )
        }.root!!
        val check = Parallel() + Activity("A") + Activity("B")

        assertEquals(expected, check)
    }
}