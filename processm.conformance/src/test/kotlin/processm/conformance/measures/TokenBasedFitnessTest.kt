package processm.conformance.measures

import processm.core.log.Helpers
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.event
import processm.core.log.Helpers.times
import processm.core.log.Helpers.trace
import processm.core.log.hierarchical.Log
import processm.core.models.petrinet.petrinet
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenBasedFitnessTest {

    val N1 = petrinet {
        P tout "a" //start
        P tin "a" * "f" tout "b" * "c" //p1
        P tin "a" * "f" tout "d" //p2
        P tin "b" * "c" tout "e" //p3
        P tin "d" tout "e" //p4
        P tin "e" tout "f" * "g" * "h" //p5
        P tin "g" * "h" //end
    }

    val N2 = petrinet {
        P tout "a" //start
        P tin "a" * "f" tout "b" * "c" //p1
        P tin "b" * "c" tout "d" //p2
        P tin "d" tout "e" //p3
        P tin "e" tout "f" * "g" * "h" //p4
        P tin "g" * "h" //end
    }

    val N3 = petrinet {
        P tout "a" //start
        P tin "a" tout "c" //p1
        P tin "a" tout "d" //p2
        P tin "c" tout "e" //p3
        P tin "d" tout "e" //p4
        P tin "e" tout "h" //p5
        P tin "h" //end
    }

    @Test
    fun `PM book fig 8_3 - trace fitness`() {

        val log = Helpers.logFromString("a c d e h")
        val trace = log.traces.first()
        val fitness = TokenBasedFitness(N1)
        with(fitness.tokenReplay(trace)) {
            assertEquals(7, p)
            assertEquals(7, c)
            assertEquals(0, m)
            assertEquals(0, r)
            assertEquals(1.0, this.fitness)
        }
    }

    @Test
    fun `PM book fig 8_3 - log fitness`() {
        val log = Helpers.logFromString("a c d e h")
        val fitness = TokenBasedFitness(N1)
        assertEquals(1.0, fitness(log))
    }

    @Test
    fun `PM book fig 8_3 - log fitness - two traces`() {
        val log = Helpers.logFromString(
            """a c d e h
            |a c d e h
        """.trimMargin()
        )
        val fitness = TokenBasedFitness(N1)
        assertEquals(1.0, fitness(log))
    }

    @Test
    fun `PM book fig 8_3 - joint`() {
        val log = Helpers.logFromString("a c d e h")
        val trace = log.traces.first()
        val fitness = TokenBasedFitness(N1)
        with(fitness.tokenReplay(trace)) {
            assertEquals(7, p)
            assertEquals(7, c)
            assertEquals(0, m)
            assertEquals(0, r)
            assertEquals(1.0, this.fitness)
        }
        assertEquals(1.0, fitness(log))
    }


    @Test
    fun `PM book fig 8_4`() {
        val trace = Helpers.logFromString("a d c e h").traces.first()
        val fitness = TokenBasedFitness(N2)
        with(fitness.tokenReplay(trace)) {
            assertEquals(6, p)
            assertEquals(6, c)
            assertEquals(1, m)
            assertEquals(1, r)
        }
    }

    @Test
    fun `PM book fig 8_5`() {
        val trace = Helpers.logFromString("a b d e g").traces.first()
        val fitness = TokenBasedFitness(N3)
        with(fitness.tokenReplay(trace)) {
            assertEquals(5, p)
            assertEquals(5, c)
            assertEquals(2, m)
            assertEquals(2, r)
        }
    }

    private val fullLog = Log(
        sequence {
            val a = event("a")
            val b = event("b")
            val c = event("c")
            val d = event("d")
            val e = event("e")
            val f = event("f")
            val g = event("g")
            val h = event("h")
            yieldAll(trace(a, c, d, e, h) * 455)
            yieldAll(trace(a, b, d, e, g) * 191)
            yieldAll(trace(a, d, c, e, h) * 177)
            yieldAll(trace(a, b, d, e, h) * 144)
            yieldAll(trace(a, c, d, e, g) * 111)
            yieldAll(trace(a, d, c, e, g) * 82)
            yieldAll(trace(a, d, b, e, h) * 56)
            yieldAll(trace(a, c, d, e, f, d, b, e, h) * 47)
            yieldAll(trace(a, d, b, e, g) * 38)
            yieldAll(trace(a, c, d, e, f, b, d, e, h) * 33)
            yieldAll(trace(a, c, d, e, f, b, d, e, g) * 14)
            yieldAll(trace(a, c, d, e, f, d, b, e, g) * 11)
            yieldAll(trace(a, d, c, e, f, c, d, e, h) * 9)
            yieldAll(trace(a, d, c, e, f, d, b, e, h) * 8)
            yieldAll(trace(a, d, c, e, f, b, d, e, g) * 5)
            yieldAll(trace(a, c, d, e, f, b, d, e, f, d, b, e, g) * 3)
            yieldAll(trace(a, d, c, e, f, d, b, e, g) * 2)
            yieldAll(trace(a, d, c, e, f, b, d, e, f, b, d, e, g) * 2)
            yield(trace(a, d, c, e, f, d, b, e, f, b, d, e, h))
            yield(trace(a, d, b, e, f, b, d, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, d, b, e, f, c, d, e, f, d, b, e, g))
        }
    )

    @Test
    fun `PM book page 253 N1`() {
        assertDoubleEquals(1.0, TokenBasedFitness(N1)(fullLog))
    }

    @Test
    fun `PM book fig 8_6`() {
        val tc = TokenBasedFitness(N2).tokenReplay(fullLog)
        assertEquals(443, tc.m)
        assertEquals(443, tc.r)
        assertDoubleEquals(0.9504, tc.fitness)
    }

    @Test
    fun `PM book fig 8_7`() {
        val tc = TokenBasedFitness(N3).tokenReplay(fullLog)
        assertEquals(10 + 146 + 566 + 461, tc.m)
        assertEquals(430 + 607, tc.r)
        assertDoubleEquals(0.8797, tc.fitness)
    }
}