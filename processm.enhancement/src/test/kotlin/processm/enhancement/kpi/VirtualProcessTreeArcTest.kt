package processm.enhancement.kpi

import processm.core.helpers.mapToSet
import processm.core.models.processtree.ProcessTree
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.models.processtree.SilentActivity
import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualProcessTreeArcTest {

    @Test
    fun `perfectProcessTree including silent`() {
        val arcs = ProcessTree
            .parse("→(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ⟲(τ, →(invite additional reviewer, ×(get review X, time-out X))), ×(accept, reject))")
            .generateArcs(includeSilent = true)
        val expected = listOf(
            "invite reviewers" to "get review 1",
            "invite reviewers" to "get review 2",
            "invite reviewers" to "get review 3",
            "invite reviewers" to "time-out 1",
            "invite reviewers" to "time-out 2",
            "invite reviewers" to "time-out 3",
            "get review 1" to "collect reviews",
            "get review 2" to "collect reviews",
            "get review 3" to "collect reviews",
            "time-out 1" to "collect reviews",
            "time-out 2" to "collect reviews",
            "time-out 3" to "collect reviews",
            "collect reviews" to "decide",
            "decide" to "τ",
            "τ" to "accept",
            "τ" to "reject",
            "τ" to "invite additional reviewer",
            "invite additional reviewer" to "get review X",
            "invite additional reviewer" to "time-out X",
            "get review X" to "τ",
            "time-out X" to "τ"
        ).mapToSet { (a, b) ->
            VirtualProcessTreeArc(
                if (a == "τ") SilentActivity() else ProcessTreeActivity(a),
                if (b == "τ") SilentActivity() else ProcessTreeActivity(b)
            )
        }
        assertEquals(expected, arcs)
    }

    @Test
    fun `perfectProcessTree excluding silent`() {
        val arcs = ProcessTree
            .parse("→(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ⟲(τ, →(invite additional reviewer, ×(get review X, time-out X))), ×(accept, reject))")
            .generateArcs(includeSilent = false)
        val expected = listOf(
            "invite reviewers" to "get review 1",
            "invite reviewers" to "get review 2",
            "invite reviewers" to "get review 3",
            "invite reviewers" to "time-out 1",
            "invite reviewers" to "time-out 2",
            "invite reviewers" to "time-out 3",
            "get review 1" to "collect reviews",
            "get review 2" to "collect reviews",
            "get review 3" to "collect reviews",
            "time-out 1" to "collect reviews",
            "time-out 2" to "collect reviews",
            "time-out 3" to "collect reviews",
            "collect reviews" to "decide",
            "invite additional reviewer" to "get review X",
            "invite additional reviewer" to "time-out X"
        ).mapToSet { (a, b) ->
            VirtualProcessTreeArc(ProcessTreeActivity(a), ProcessTreeActivity(b))
        }
        assertEquals(expected, arcs)
    }

    @Test
    fun `loop with sequences`() {
        val arcs = ProcessTree
            .parse("→(a, ⟲(→(b,c), →(d, e), →(f, g)), h)")
            .generateArcs()
        val expected = listOf(
            "ab",
            "bc",
            "ch",
            "cd",
            "de",
            "eb",
            "cf",
            "fg",
            "gb"
        ).mapToSet { s ->
            val a = s[0].toString()
            val b = s[1].toString()
            VirtualProcessTreeArc(
                if (a == "τ") SilentActivity() else ProcessTreeActivity(a),
                if (b == "τ") SilentActivity() else ProcessTreeActivity(b)
            )
        }
        assertEquals(expected, arcs)
    }
}