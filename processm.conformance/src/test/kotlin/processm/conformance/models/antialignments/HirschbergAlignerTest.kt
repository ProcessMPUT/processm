package processm.conformance.models.antialignments

import processm.core.log.Event
import processm.core.log.Helpers
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.mutableAttributeMapOf
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import kotlin.test.*

class HirschbergAlignerTest {
    private data class MyActivity(override val name: String, override val isSilent: Boolean = false) : Activity

//    @Test
//    fun `nwScore regression`() {
//        val activities = "agtacgca".map { MyActivity(it.toString(), isSilent = it == '_') }
//        val trace = Helpers.logFromString("t a t g c").traces.single()
//        val events = trace.events.toList()
//        with(HirschbergAligner(ReplayModel(activities))) {
//            assertContentEquals(intArrayOf(8, 7, 6, 7, 6, 5), nwScore(activities, events))
//            assertContentEquals(intArrayOf(4, 3, 2, 3, 4, 5), nwScore(activities.subList(0, 4), events))
//            assertContentEquals(
//                intArrayOf(4, 3, 2, 3, 4, 5),
//                nwScore(activities.subList(4, activities.size).asReversed(), events.asReversed())
//            )
//        }
//    }

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

    @Test
    fun `missing event 1`() {
        val activities =
            "invite reviewers, _before review 1*, _before review 3*, time-out 1, time-out 3, _after review 1*, _after review 3*, _before review 2*, time-out 2, _after review 2*, collect reviews, decide, _after decide*, reject, _end*"
                .split(",").map {
                    val text = it.trim()
                    if (text.last() == '*') MyActivity(text.substring(0, text.length - 1), isSilent = true)
                    else MyActivity(text, isSilent = false)
                }
        val events =
            "invite reviewers, get review 2, time-out 1, time-out 3, collect reviews, decide, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, get review X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, time-out X, invite additional reviewer, get review X, reject"
//            "invite reviewers, get review 2, time-out 1, time-out 3, collect reviews, decide, reject"
                .split(",").map {
                    Event(mutableAttributeMapOf(CONCEPT_NAME to it.trim()))
                }
        val alignment = HirschbergAligner(ReplayModel(activities)).align(Trace(events.asSequence()))
        assertContentEquals(
            events.mapNotNull { it.conceptName },
            alignment.steps.mapNotNull { it.logMove?.conceptName })
        assertContentEquals(activities, alignment.steps.mapNotNull { it.modelMove })
    }

    @Test
    fun `missing event 2`() {
        val activities =
            "invite reviewers, _before review 1*, _before review 3*, time-out 1, time-out 3, _after review 1*, _after review 3*, _before review 2*, time-out 2, _after review 2*, collect reviews, decide, _after decide*, reject, _end*"
                .split(",").map {
                    val text = it.trim()
                    if (text.last() == '*') MyActivity(text.substring(0, text.length - 1), isSilent = true)
                    else MyActivity(text, isSilent = false)
                }
        val events =
            "invite reviewers, get review 2, time-out 1, time-out 3, collect reviews, decide, reject"
                .split(",").map {
                    Event(mutableAttributeMapOf(CONCEPT_NAME to it.trim()))
                }
        val alignment = HirschbergAligner(ReplayModel(activities)).align(Trace(events.asSequence()))
        assertContentEquals(
            events.mapNotNull { it.conceptName },
            alignment.steps.mapNotNull { it.logMove?.conceptName })
        assertContentEquals(activities, alignment.steps.mapNotNull { it.modelMove })
    }
}