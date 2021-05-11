package processm.core.models.petrinet

import kotlin.test.Test
import kotlin.test.assertEquals

class PetriNetDSLTest {

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
        val expected = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )

        val net = petrinet {
            P tout "a"
            P tin "a" * "f" tout "b" * "c"
            P tin "a" * "f" tout "d"
            P tin "b" * "c" tout "e"
            P tin "d" tout "e"
            P tin "e" tout "g" * "h" * "f"
            P tin "g" * "h"
        }

        assertEquals(expected, net)
    }

    @Test
    fun `PM book Fig 3 2 with a silent transition`() {
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
        val d = Transition("_d", listOf(c2), listOf(c4), isSilent = true)
        val e = Transition("e", listOf(c3, c4), listOf(c5))
        val f = Transition("f", listOf(c5), listOf(c1, c2))
        val g = Transition("g", listOf(c5), listOf(end))
        val h = Transition("h", listOf(c5), listOf(end))
        val expected = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )

        val net = petrinet {
            P tout "a"
            P tin "a" * "f" tout "b" * "c"
            P tin "a" * "f" tout "_d"
            P tin "b" * "c" tout "e"
            P tin "_d" tout "e"
            P tin "e" tout "g" * "h" * "f"
            P tin "g" * "h"
        }

        assertEquals(expected, net)
    }
}