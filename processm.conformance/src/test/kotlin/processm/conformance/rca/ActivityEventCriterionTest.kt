package processm.conformance.rca

import processm.conformance.alignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityEventCriterionTest {

    @Test
    fun test() {
        val alignments = listOf(
            alignment {
                "a" with ("person" to "A") executing "a"
                "b" with ("person" to "A") executing "b"
                "c" executing "c"
                "d" executing "d"
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                "b" with ("person" to "B") executing "b"
                "c" executing "c"
                "d" executing "d"
            },
            alignment {
                "a" with ("person" to "A") executing "a"
                null executing "c"
                "b" with ("person" to "B") executing "b"
                "d" executing "d"
                "c" executing null
            },
            alignment {
                "a" with ("person" to "B") executing "a"
                null executing "c"
                "b" with ("person" to "A") executing "b"
                "d" executing "d"
                "c" executing null
            }
        )
        val criterion = ActivityEventCriterion("c", "c")
        val ds = criterion.partition(alignments)
        assertEquals(2, ds.positive.size)
        assertTrue { alignments[2] in ds.positive }
        assertTrue { alignments[3] in ds.positive }
        assertEquals(2, ds.negative.size)
        assertTrue { alignments[0] in ds.negative }
        assertTrue { alignments[1] in ds.negative }
    }
}