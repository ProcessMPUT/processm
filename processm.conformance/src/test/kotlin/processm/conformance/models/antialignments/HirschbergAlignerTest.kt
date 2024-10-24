package processm.conformance.models.antialignments

import processm.core.log.Helpers
import processm.core.models.commons.Activity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HirschbergAlignerTest {
    private data class MyActivity(override val name: String, override val isSilent: Boolean = false) : Activity

    @Test
    fun test() {
        val activities = "ab_d".map { MyActivity(it.toString(), isSilent = it == '_') }
        val trace = Helpers.logFromString("a b d").traces.single()
        val events = trace.events.toList()
        with(HirschbergAligner(ReplayModel(activities))) {
            val a = this.align(trace, trace.count * 2)!!
            with(a.steps[0]) {
                assertEquals(activities[0], modelMove)
                assertEquals(events[0], logMove)
            }
            with(a.steps[1]) {
                assertEquals(activities[1], modelMove)
                assertEquals(events[1], logMove)
            }
            with(a.steps[2]) {
                assertEquals(activities[2], modelMove)
                assertNull(logMove)
            }
            with(a.steps[3]) {
                assertEquals(activities[3], modelMove)
                assertEquals(events[2], logMove)
            }
            assertEquals(0, a.cost)
        }
    }

    @Test
    fun wikipedia() {
        val activities = "agtacgca".map { MyActivity(it.toString(), isSilent = it == '_') }
        val trace = Helpers.logFromString("t a t g c").traces.single()
        val events = trace.events.toList()
        with(HirschbergAligner(ReplayModel(activities))) {
            val a = this.align(trace, 999)!!
            assertNotNull(a)
            assertEquals(9, a.steps.size)
            with(a.steps[0]) {
                assertEquals(activities[0], modelMove)
                assertNull(logMove)
            }
            with(a.steps[1]) {
                assertEquals(activities[1], modelMove)
                assertNull(logMove)
            }
            with(a.steps[2]) {
                assertEquals(activities[2], modelMove)
                assertEquals(events[0], logMove)
            }
            with(a.steps[3]) {
                assertEquals(activities[3], modelMove)
                assertEquals(events[1], logMove)
            }
            // The ordering of the following two steps is arbitrary. I believe the algorithm is stable,
            // but should the test fail check whether they did not swap places
            with(a.steps[4]) {
                assertEquals(activities[4], modelMove)
                assertNull(logMove)
            }
            with(a.steps[5]) {
                assertNull(modelMove)
                assertEquals(events[2], logMove)
            }
            with(a.steps[6]) {
                assertEquals(activities[5], modelMove)
                assertEquals(events[3], logMove)
            }
            with(a.steps[7]) {
                assertEquals(activities[6], modelMove)
                assertEquals(events[4], logMove)
            }
            with(a.steps[8]) {
                assertEquals(activities[7], modelMove)
                assertNull(logMove)
            }
            assertEquals(5, a.cost)
        }
    }
}