package processm.core.models.petrinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PetriNetTests {

    @Test
    fun `PM book Fig 3 2`() {
        val start = Place()
        val c1 = Place()
        val c2 = Place()
        val c3 = Place()
        val c4 = Place()
        val c5 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(c1, c2))
        val b = Transition("b", listOf(c1), listOf(c3))
        val c = Transition("c", listOf(c1), listOf(c3))
        val d = Transition("d", listOf(c2), listOf(c4))
        val e = Transition("e", listOf(c3, c4), listOf(c5))
        val f = Transition("f", listOf(c5), listOf(c1, c2))
        val g = Transition("g", listOf(c5), listOf(end))
        val h = Transition("h", listOf(c5), listOf(end))
        val net = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )

        assertEquals(listOf(a, b, c, d, e, f, g, h), net.activities.toList())
        assertEquals(listOf(start, c1, c2, c3, c4, c5, end), net.places)
        assertEquals(listOf(a), net.startActivities.toList())
        assertEquals(listOf(g, h), net.endActivities.toList())
        val decisionPoints = net.decisionPoints.toList()
        assertEquals(2, decisionPoints.size)
        assertTrue(DecisionPoint(setOf(c1), setOf(b, c)) in decisionPoints)
        assertTrue(DecisionPoint(setOf(c5), setOf(f, g, h)) in decisionPoints)
    }
}
