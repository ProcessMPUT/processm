package processm.core.models.petrinet

import kotlin.test.*

class PetriNetTests {

    private fun createNet(
        a: String,
        b: String,
        c: String,
        d: String,
        e: String,
        f: String,
        g: String,
        h: String
    ): PetriNet {
        val start = Place()
        val c1 = Place()
        val c2 = Place()
        val c3 = Place()
        val c4 = Place()
        val c5 = Place()
        val end = Place()
        val a = Transition(a, listOf(start), listOf(c1, c2))
        val b = Transition(b, listOf(c1), listOf(c3))
        val c = Transition(c, listOf(c1), listOf(c3))
        val d = Transition(d, listOf(c2), listOf(c4))
        val e = Transition(e, listOf(c3, c4), listOf(c5))
        val f = Transition(f, listOf(c5), listOf(c1, c2))
        val g = Transition(g, listOf(c5), listOf(end))
        val h = Transition(h, listOf(c5), listOf(end))
        val net = PetriNet(
            listOf(start, c1, c2, c3, c4, c5, end),
            listOf(a, b, c, d, e, f, g, h),
            Marking(start),
            Marking(end)
        )
        return net
    }

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

    @Test
    fun `equals test simple`() {
        val a = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        val b = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        assertNotSame(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a, b)
    }

    @Test
    fun `equals test all names identical`() {
        val a = createNet("x", "x", "x", "x", "x", "x", "x", "x")
        val b = createNet("x", "x", "x", "x", "x", "x", "x", "x")
        assertNotSame(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a, b)
    }

    @Test
    fun `equals test correctly swapped names`() {
        val a = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        val b = createNet("a", "c", "b", "d", "e", "f", "h", "g")
        assertNotSame(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a, b)
    }

    @Test
    fun `equals test incorrectly swapped names`() {
        val a = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        val b = createNet("a", "b", "d", "c", "e", "f", "g", "h")
        assertNotSame(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b)
    }

    @Test
    fun `equals test incorrectly swapped names 2`() {
        val a = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        val b = createNet("h", "b", "c", "d", "e", "f", "g", "a")
        assertNotSame(a, b)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b)
    }

    @Test
    fun `equals test changed names`() {
        val a = createNet("a", "b", "c", "d", "e", "f", "g", "h")
        val b = createNet("A", "B", "c", "d", "e", "f", "g", "h")
        assertNotSame(a, b)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b)
    }

    @Test
    fun forwardSearch() {
        val net = petrinet {
            P tout "a" * "b" * "_1"
            P tin "_1" tout "c" * "d"
            P tin "_1" tout "e" * "_2"
            P tin "_2" tout "d"
        }
        val transitionSets = net.forwardSearch(net.places[0]).toList()
        assertTrue { transitionSets.all { set -> set.all { !it.isSilent } } }
        val names = transitionSets.mapTo(HashSet()) { set -> set.mapTo(HashSet()) { it.name } }
        val expected = setOf(
            setOf("a"),
            setOf("b"),
            setOf("d"),
            setOf("c", "d"),
            setOf("c", "e"),
            setOf("d", "e")
        )
        assertEquals(expected, names)
    }
}
