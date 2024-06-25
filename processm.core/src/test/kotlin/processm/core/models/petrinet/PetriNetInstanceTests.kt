package processm.core.models.petrinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PetriNetInstanceTests {

    fun PetriNetInstance.expecting(vararg transitions: Transition, final: Boolean? = null): List<TransitionExecution> {
        assertEquals(final ?: transitions.isEmpty(), this.isFinalState)
        val available = this.availableActivityExecutions.toList()
        assertEquals(transitions.size, available.size)
        for (transition in transitions)
            assertTrue(available.any { a -> transition == a.activity })

        return available
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

        val instance = net.createInstance()
        assertEquals(Marking(start), instance.currentState)

        with(instance) {
            expecting(a)[0].execute()
            expecting(b, c, d).first { it.activity == c }.execute()
            expecting(d)[0].execute()
            expecting(e)[0].execute()
            expecting(f, g, h).first { it.activity == f }.execute()
            expecting(b, c, d).first { it.activity == d }.execute()
            expecting(b, c).first { it.activity == b }.execute()
            expecting(e)[0].execute()
            expecting(f, g, h).first { it.activity == g }.execute()
            expecting()
        }

        assertEquals(Marking(end), instance.currentState)
    }

    @Test
    fun `PM book Fig 3 4 c`() {
        val i1 = Place()
        val i2 = Place()
        val i3 = Place()
        val i4 = Place()
        val i5 = Place()
        val out = Place()
        val t1 = Transition("t1", listOf(i1), listOf(out))
        val t2 = Transition("t2", listOf(i2), listOf(out))
        val t3 = Transition("t3", listOf(i3), listOf(out))
        val t4 = Transition("t4", listOf(i4), listOf(out))
        val t5 = Transition("t5", listOf(i5), listOf(out))
        val net = PetriNet(
            listOf(i1, i2, i3, i4, i5, out),
            listOf(t1, t2, t3, t4, t5),
            Marking(mapOf(i1 to 5, i2 to 5, i3 to 5, i4 to 5, i5 to 5)),
            Marking(mapOf(out to 25))
        )

        val instance = net.createInstance()
        assertEquals(Marking(mapOf(i1 to 5, i2 to 5, i3 to 5, i4 to 5, i5 to 5)), instance.currentState)

        with(instance) {
            for (i in 1..4) {
                expecting(t1, t2, t3, t4, t5).first { it.activity == t1 }.execute()
                expecting(t1, t2, t3, t4, t5).first { it.activity == t2 }.execute()
                expecting(t1, t2, t3, t4, t5).first { it.activity == t3 }.execute()
                expecting(t1, t2, t3, t4, t5).first { it.activity == t4 }.execute()
                expecting(t1, t2, t3, t4, t5).first { it.activity == t5 }.execute()
            }

            expecting(t1, t2, t3, t4, t5).first { it.activity == t5 }.execute()
            expecting(t1, t2, t3, t4).first { it.activity == t4 }.execute()
            expecting(t1, t2, t3).first { it.activity == t3 }.execute()
            expecting(t1, t2).first { it.activity == t2 }.execute()
            expecting(t1).first { it.activity == t1 }.execute()
            expecting()
        }

        assertEquals(Marking(mapOf(out to 25)), instance.currentState)
    }

    @Test
    fun `PM book Fig 3 14`() {
        val start = Place()
        val a1 = Place()
        val b1 = Place()
        val b2 = Place()
        val b3 = Place()
        val c1 = Place()
        val c2 = Place()
        val c3 = Place()
        val d1 = Place()
        val d2 = Place()
        val d3 = Place()
        val e1 = Place()
        val e2 = Place()
        val e3 = Place()
        val f1 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(a1))
        val t1 = Transition("t1", listOf(a1), listOf(b1), true)
        val t2 = Transition("t2", listOf(a1), listOf(b2), true)
        val t3 = Transition("t3", listOf(a1), listOf(b1, b3), true)
        val t4 = Transition("t4", listOf(a1), listOf(b2, b3), true)
        val t5 = Transition("t5", listOf(a1), listOf(b1, b2, b3), true)
        val u1 = Transition("u1", listOf(b1), listOf(c1), true)
        val u2 = Transition("u2", listOf(b2), listOf(c2), true)
        val u3 = Transition("u3", listOf(b3), listOf(c3), true)
        val b = Transition("b", listOf(c1), listOf(d1))
        val c = Transition("c", listOf(c2), listOf(d2))
        val d = Transition("d", listOf(c3), listOf(d3))
        val w1 = Transition("w1", listOf(d1), listOf(e1), true)
        val w2 = Transition("w2", listOf(d2), listOf(e2), true)
        val w3 = Transition("w3", listOf(d3), listOf(e3), true)
        val x1 = Transition("x1", listOf(e1), listOf(f1), true)
        val x2 = Transition("x2", listOf(e2), listOf(f1), true)
        val x3 = Transition("x3", listOf(e1, e3), listOf(f1), true)
        val x4 = Transition("x4", listOf(e2, e3), listOf(f1), true)
        val x5 = Transition("x5", listOf(e1, e2, e3), listOf(f1), true)
        val e = Transition("e", listOf(f1), listOf(end))

        val net = PetriNet(
            listOf(start, a1, b1, b2, b3, c1, c2, c3, d1, d2, d3, e1, e2, e3, f1, end),
            listOf(a, t1, t2, t3, t4, t5, u1, u2, u3, b, c, d, w1, w2, w3, x1, x2, x3, x4, x5, e),
            Marking(start),
            Marking(end)
        )

        val instance = net.createInstance()
        assertEquals(Marking(start), instance.currentState)

        with(instance) {
            expecting(a)[0].execute()
            expecting(t1, t2, t3, t4, t5).first { it.activity == t4 }.execute()
            expecting(u2, u3).first { it.activity == u3 }.execute()
            expecting(u2, d).first { it.activity == d }.execute()
            expecting(u2, w3).first { it.activity == w3 }.execute()
            expecting(u2)[0].execute()
            expecting(c)[0].execute()
            expecting(w2)[0].execute()
            expecting(x2, x4).first { it.activity == x4 }.execute()
            expecting(e)[0].execute()
            expecting()
        }

        assertEquals(Marking(end), instance.currentState)

        instance.setState(Marking(start))
        with(instance) {
            expecting(a)[0].execute()
            expecting(t1, t2, t3, t4, t5).first { it.activity == t4 }.execute()
            expecting(u2, u3).first { it.activity == u3 }.execute()
            expecting(u2, d).first { it.activity == d }.execute()
            expecting(u2, w3).first { it.activity == w3 }.execute()
            expecting(u2)[0].execute()
            expecting(c)[0].execute()
            expecting(w2)[0].execute()
            expecting(x2, x4).first { it.activity == x2 }.execute()
            expecting(e)[0].execute()
            expecting(final = false)
        }

        assertEquals(Marking(e3, end), instance.currentState)
    }

    @Test
    fun `PM book Fig 7 3`() {
        val start = Place()
        val p1 = Place()
        val p2 = Place()
        val p3 = Place()
        val p4 = Place()
        val p5 = Place()
        val end = Place()
        val a = Transition("a", listOf(start), listOf(p1, p2, p5))
        val b = Transition("b", listOf(p1), listOf(p3))
        val c = Transition("c", listOf(p2), listOf(p4))
        val d = Transition("d", listOf(), listOf())
        val e = Transition("e", listOf(p3, p4, p5), listOf(end))
        val net = PetriNet(
            listOf(start, p1, p2, p3, p4, p5, end),
            listOf(a, b, c, d, e),
            Marking(start),
            Marking(end)
        )

        val instance = net.createInstance()
        assertEquals(Marking(start), instance.currentState)

        with(instance) {
            expecting(a, d).first { it.activity == d }.execute()
            expecting(a, d).first { it.activity == a }.execute()
            expecting(b, c, d).first { it.activity == b }.execute()
            expecting(c, d).first { it.activity == d }.execute()
            expecting(c, d).first { it.activity == c }.execute()
            expecting(d, e).first { it.activity == d }.execute()
            expecting(d, e).first { it.activity == e }.execute()
            expecting(d, final = true)[0].execute()
            expecting(d, final = true)
        }

        assertEquals(Marking(end), instance.currentState)
    }
}
