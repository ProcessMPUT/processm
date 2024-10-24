package processm.conformance.models.antialignments

import processm.core.log.Helpers.logFromString
import processm.core.models.commons.Activity
import kotlin.test.Test
import kotlin.test.assertEquals

class CountUnmatchedLogMovesInReplayModelTest {

    private data class MyActivity(override val name: String, override val isSilent: Boolean = false) : Activity


    @Test
    fun test() {
        val activities = "ab_d".map { MyActivity(it.toString(), isSilent = it == '_') }
        val events = logFromString("a b d").traces.single().events.toList()
        val count = CountUnmatchedLogMovesInReplayModel(ReplayModel(activities))
        count.reset()
        // initial state
        assertEquals(0, count.compute(0, events, ReplayModelState(0), null))

        // synchronous move
        assertEquals(0, count.compute(1, events, ReplayModelState(0), activities[0]))
        // model-only move
        assertEquals(1, count.compute(0, events, ReplayModelState(0), activities[0]))
        // log-only move
        assertEquals(0, count.compute(1, events, ReplayModelState(0), null))

        // synchronous move, synchronous move
        assertEquals(0, count.compute(2, events, ReplayModelState(1), activities[1]))
        // model-only move, model-only move
        assertEquals(2, count.compute(0, events, ReplayModelState(1), activities[1]))
        // synchronous move, model-only move
        assertEquals(1, count.compute(1, events, ReplayModelState(1), activities[1]))
        // synchronous move, log-only move
        assertEquals(0, count.compute(2, events, ReplayModelState(1), null))

        // synchronous move, synchronous move, model-only (silent)
        assertEquals(0, count.compute(2, events, ReplayModelState(2), activities[2]))
        // model-only move, model-only move, model-only (silent)
        assertEquals(2, count.compute(0, events, ReplayModelState(2), activities[2]))
        // synchronous move, synchronous move, log-only move
        assertEquals(0, count.compute(3, events, ReplayModelState(2), null))

        // synchronous move, synchronous move, model-only (silent), synchronous move
        assertEquals(0, count.compute(3, events, ReplayModelState(3), activities[3]))
        // model-only move, model-only move, model-only (silent), model-only move
        assertEquals(3, count.compute(0, events, ReplayModelState(3), activities[3]))
    }
}