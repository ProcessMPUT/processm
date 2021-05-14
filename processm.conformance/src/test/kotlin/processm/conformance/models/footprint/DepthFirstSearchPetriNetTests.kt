package processm.conformance.models.footprint

import processm.core.helpers.allPermutations
import processm.core.log.Helpers
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Trace
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.petrinet.Marking
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import kotlin.test.Test
import kotlin.test.assertEquals

class DepthFirstSearchPetriNetTests {

    @Test
    fun `PM book Fig 3 2 conforming log`() {
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

        val log = logFromString(
            """
                a b d e g
                a b d e h
                a b d e f c d e h
                a c d e g
                a c d e h
                a c d e f b d e g
                a d c e f d c e f b d e h
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 4.0 / 64.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 2 non-conforming log`() {
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

        val log = logFromString(
            """
                a c e f b e f d b e g
                a b c d e h
                b d e f c d e h
                a c d f b d e g
                a c d e f e h
                a g
                a b c d e f d c b e f g h
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 12.0 / 64.0, model.fitness)
        assertEquals(1.0 - 2.0 / 64.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 4 c conforming log`() {
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
            listOf(i1, i2, i3, i4, i5),
            listOf(t1, t2, t3, t4, t5),
            Marking(mapOf(i1 to 5, i2 to 5, i3 to 5, i4 to 5, i5 to 5)),
            Marking(mapOf(out to 25))
        )

        val allMoves = List(5) { t1 } + List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }
        val limit = 10000
        val log = allMoves
            .allPermutations()
            .take(limit)
            .map { activities -> Trace(activities.asSequence().map { Helpers.event(it.name) }) }


        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 18.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 4 c non-conforming log`() {
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
            listOf(i1, i2, i3, i4, i5),
            listOf(t1, t2, t3, t4, t5),
            Marking(mapOf(i1 to 5, i2 to 5, i3 to 5, i4 to 5, i5 to 5)),
            Marking(mapOf(out to 25))
        )

        // missing t1s
        val allMoves = List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }
        val limit = 10000
        val log = allMoves
            .allPermutations()
            .take(limit)
            .map { activities -> Trace(activities.asSequence().map { Helpers.event(it.name) }) }


        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 19.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 14 conforming log`() {
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

        val log = logFromString(
            """
                a b e
                a c e
                a b d e
                a d b e
                a c d e
                a d c e
                a b c d e
                a b d c e
                a d b c e
                a c b d e
                a c d b e
                a d c b e
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 7.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 3 14 non-conforming log`() {
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

        val log = logFromString(
            """
                a b e z
                a c 
                a d e
                a b b c e
                a e
                d c b e
                x y z
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 9.0 / 64.0, model.fitness)
        assertEquals(1.0 - 11.0 / 25.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 3 conforming log`() {
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

        val log = logFromString(
            """
                a b c e
                a c b e
                d a d b d c d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d e d d d d d d d
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        // the execution of d at the end of the trace requires the model to pass behind the final marking
        assertEquals(1.0, model.fitness)
        assertEquals(1.0, model.precision)
    }

    @Test
    fun `PM book Fig 7 3 non-conforming log`() {
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

        val log = logFromString(
            """
                a b e
                a c e
                d a z d b d d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d d d d d d d d
                d a e d d b c e
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        // the execution of d at the end of the trace requires the model to pass behind the final marking
        assertEquals(1.0 - 6.0 / 36.0, model.fitness)
        assertEquals(1.0 - 2.0 / 25.0, model.precision)
    }

    @Test
    fun `Flower model conforming log`() {
        val start = Place()
        val center = Place()
        val end = Place()
        val net = PetriNet(
            places = listOf(start, center, end),
            transitions =
            "abcdefghijklmnopqrstuwvxyz".map { Transition(it.toString(), listOf(center), listOf(center)) }
                    +
                    listOf(
                        Transition("", listOf(start), listOf(center), true),
                        Transition("", listOf(center), listOf(end), true),
                    ),
            initialMarking = Marking(start),
            finalMarking = Marking(end)
        )

        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Flower model non-conforming log`() {
        val start = Place()
        val center = Place()
        val end = Place()
        val net = PetriNet(
            places = listOf(start, center, end),
            transitions =
            "abcdefghijklmnopqrstuwvxyz".map { Transition(it.toString(), listOf(center), listOf(center)) }
                    +
                    listOf(
                        Transition("", listOf(start), listOf(center), true),
                        Transition("", listOf(center), listOf(end), true),
                    ),
            initialMarking = Marking(start),
            finalMarking = Marking(end)
        )

        val log = logFromString(
            """
                1 a b c 5 d e f 09 g h i 13 j k l m n o p q r s t u w v x y z
                z 2 y x v 6 w u t 10 s r q 14 p o n m l k j i h g f e d c b a
                a a 3 a a a 7 a a a 11 a a a 15 a a z z z z z z z z z z z z z
                z z z 4 z z z 8 z z z 12 z z z 16 z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 46.0 / (42.0 * 42.0), model.fitness)
        assertEquals(1.0 - 636.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models in loop conforming log`() {
        val flower1Place = Place()
        val flower2Place = Place()
        val flower1 = "abcdefghijklm".map {
            Transition(it.toString(), inPlaces = listOf(flower1Place), outPlaces = listOf(flower1Place))
        }
        val flower2 = "nopqrstuwvxyz".map {
            Transition(it.toString(), inPlaces = listOf(flower2Place), outPlaces = listOf(flower2Place))
        }
        val loopStartPlace = Place()
        val loopEndPlace = Place()
        val loopStart = Transition("ls", listOf(loopStartPlace), listOf(flower1Place, flower2Place), true)
        val loopEnd = Transition("le", listOf(flower1Place, flower2Place), listOf(loopEndPlace), true)
        val redo = Transition("redo", listOf(loopEndPlace), listOf(loopStartPlace), true)

        val net = PetriNet(
            places = listOf(loopStartPlace, flower1Place, flower2Place, loopEndPlace),
            transitions = listOf(loopStart, loopEnd, redo) + flower1 + flower2,
            initialMarking = Marking(loopStartPlace),
            finalMarking = Marking(loopEndPlace)
        )

        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0, model.fitness)
        assertEquals(1.0 - 622.0 / (26.0 * 26.0), model.precision)
    }

    @Test
    fun `Parallel flower models in loop non-conforming log`() {
        val flower1Place = Place()
        val flower2Place = Place()
        val flower1 = "abcdefghijklm".map {
            Transition(it.toString(), inPlaces = listOf(flower1Place), outPlaces = listOf(flower1Place))
        }
        val flower2 = "nopqrstuwvxyz".map {
            Transition(it.toString(), inPlaces = listOf(flower2Place), outPlaces = listOf(flower2Place))
        }
        val loopStartPlace = Place()
        val loopEndPlace = Place()
        val loopStart = Transition("ls", listOf(loopStartPlace), listOf(flower1Place, flower2Place), true)
        val loopEnd = Transition("le", listOf(flower1Place, flower2Place), listOf(loopEndPlace), true)
        val redo = Transition("redo", listOf(loopEndPlace), listOf(loopStartPlace), true)

        val net = PetriNet(
            places = listOf(loopStartPlace, flower1Place, flower2Place, loopEndPlace),
            transitions = listOf(loopStart, loopEnd, redo) + flower1 + flower2,
            initialMarking = Marking(loopStartPlace),
            finalMarking = Marking(loopEndPlace)
        )

        val log = logFromString(
            """
                a a a a a a a a a a a a a z 1
                a b c 1 d e f g h i j k l m
                z y x v w u t s r q 1 p o n 2
                2 z z z z z z z z 1 z z z z a
            """
        )

        val startTime = System.currentTimeMillis()
        val dfs = DepthFirstSearch(net)
        val model = dfs.assess(log.toFlatSequence())
        val time = System.currentTimeMillis() - startTime

        println("Calculated footprint-based conformance model in ${time}ms\tfitness: ${model.fitness}\tprecision: ${model.precision}\n$model")
        assertEquals(1.0 - 14.0 / (28.0 * 28.0), model.fitness)
        assertEquals(1.0 - 672.0 / (26.0 * 26.0), model.precision)
    }
}
