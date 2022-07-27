package processm.conformance

import processm.core.models.petrinet.*

/**
 * The Petri nets for tests.
 */
object PetriNets {
    /**
     * The Petri net without places and transitions.
     */
    val empty: PetriNet = petrinet {}

    /**
     * A Petri net based on Fig. 3.2 in the Process Mining: Data Science in Action book.
     */
    val fig32: PetriNet = petrinet {
        P tout "a"
        P tin "a" * "f" tout "b" * "c"
        P tin "a" * "f" tout "d"
        P tin "b" * "c" tout "e"
        P tin "d" tout "e"
        P tin "e" tout "g" * "h" * "f"
        P tin "g" * "h"
    }

    /**
     * A Petri net based on Fig. 3.4c in the Process mining: Data Science in Action book.
     */
    val fig34c: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
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
        PetriNet(
            listOf(i1, i2, i3, i4, i5),
            listOf(t1, t2, t3, t4, t5),
            Marking(mapOf(i1 to 5, i2 to 5, i3 to 5, i4 to 5, i5 to 5)),
            Marking(mapOf(out to 25))
        )
    }

    /**
     * A Petri net based on Fig. 3.14 in the Process mining: Data Science in Action book.
     */
    val fig314: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
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
        net
    }

    /**
     * A Petri net based on Fig. 6.24 network N3 in the Process Mining: Data Science in Action book.
     */
    val fig624N3 = petrinet {
        P tout "a"
        P tin "a" * "b" * "d" * "c" * "e" * "f" tout "b" * "d" * "c" * "e" * "f" * "g" * "h"
        P tin "g" * "h"
    }

    /**
     * A Petri net based on Fig. 7.3 in the Process mining: Data Science in Action book.
     */
    val fig73: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
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
        PetriNet(
            listOf(start, p1, p2, p3, p4, p5, end),
            listOf(a, b, c, d, e),
            Marking(start),
            Marking(end)
        )
    }

    val fig82N1 = petrinet {
        P tout "a" //start
        P tin "a" * "f" tout "b" * "c" //p1
        P tin "a" * "f" tout "d" //p2
        P tin "b" * "c" tout "e" //p3
        P tin "d" tout "e" //p4
        P tin "e" tout "f" * "g" * "h" //p5
        P tin "g" * "h" //end
    }

    val fig82N2 = petrinet {
        P tout "a" //start
        P tin "a" * "f" tout "b" * "c" //p1
        P tin "b" * "c" tout "d" //p2
        P tin "d" tout "e" //p3
        P tin "e" tout "f" * "g" * "h" //p4
        P tin "g" * "h" //end
    }

    val fig82N3 = petrinet {
        P tout "a" //start
        P tin "a" tout "c" //p1
        P tin "a" tout "d" //p2
        P tin "c" tout "e" //p3
        P tin "d" tout "e" //p4
        P tin "e" tout "h" //p5
        P tin "h" //end
    }

    /**
     * A Petri net flower model made of the activities a, b, c, ..., z.
     */
    val azFlower: PetriNet = flower(*"abcdefghijklmnopqrstuwvxyz".map { it.toString() }.toTypedArray())

    fun flower(vararg activities: String): PetriNet {
        val start = Place()
        val center = Place()
        val end = Place()
        return PetriNet(
            places = listOf(start, center, end),
            transitions =
            activities.map { Transition(it, listOf(center), listOf(center)) }
                    +
                    listOf(
                        Transition("", listOf(start), listOf(center), true),
                        Transition("", listOf(center), listOf(end), true),
                    ),
            initialMarking = Marking(start),
            finalMarking = Marking(end)
        )
    }

    /**
     * A Petri net made of two flower models: the first containing activities a-m, the second containing activities n-z.
     */
    val parallelFlowers: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
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

        PetriNet(
            places = listOf(loopStartPlace, flower1Place, flower2Place, loopEndPlace),
            transitions = listOf(loopStart, loopEnd, redo) + flower1 + flower2,
            initialMarking = Marking(loopStartPlace),
            finalMarking = Marking(loopEndPlace)
        )
    }

    /**
     * A Petri net implementing a sequence of activities a -> c -> d -> e -> h.
     */
    val sequence: PetriNet = getSequence("a", "c", "d", "e", "h")

    /**
     * Produces a Petri net that allows only for the sequential execution of the given activities in the given order.
     */
    fun getSequence(vararg activities: String) = petrinet {
        var prev: String? = null
        for (activity in activities) {
            if (prev !== null)
                P tin prev tout activity
            else
                P tout activity
            prev = activity
        }
        P tin activities.last()
    }

    /**
     * A sequence of two "a" activities.
     */
    val duplicateA: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val start = Place()
        val middle = Place()
        val end = Place()
        PetriNet(
            places = listOf(start, middle, end),
            transitions = listOf(
                Transition("a", listOf(start), listOf(middle)),
                Transition("a", listOf(middle), listOf(end))
            ),
            initialMarking = Marking(start),
            finalMarking = Marking(end),
        )
    }

    /**
     * A Petri net from Fig. 1 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig1: PetriNet = petrinet {
        P tout "A"
        P tin "A" tout "B" * "C"
        P tin "B" tout "E"
        P tin "E" * "F" tout "I"
        P tin "B" * "C" tout "D"
        P tin "D" tout "E" * "F"
        P tin "C" tout "_tau" * "G"
        P tin "_tau" * "G" tout "H"
        P tin "H" tout "F"
        P tin "I"
    }

    /**
     * A Petri net from Fig. 2 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig2: PetriNet = getSequence("A", "B", "D", "E", "I")

    /**
     * A Petri net from Fig. 3 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig3: PetriNet = flower("A", "B", "C", "D", "E", "F", "G", "H", "I")

    /**
     * A Petri net from Fig. 4 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig4: PetriNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val start = Place()
        val end = Place()

        val parts = listOf(
            getSequence("A", "B", "D", "E", "I"),
            getSequence("A", "C", "D", "G", "H", "F", "I"),
            getSequence("A", "C", "G", "D", "H", "F", "I"),
            getSequence("A", "C", "H", "D", "F", "I"),
            getSequence("A", "C", "D", "H", "F", "I")
        )

        fun PetriNet.pl(): List<Place> = places.filter { it !in initialMarking && it !in finalMarking }
        fun PetriNet.tr(): List<Transition> = transitions.map {
            when {
                initialMarking.keys.first() in it.inPlaces -> it.copy(inPlaces = listOf(start))
                finalMarking.keys.first() in it.outPlaces -> it.copy(outPlaces = listOf(end))
                else -> it
            }
        }

        PetriNet(
            places = parts.flatMap { it.pl() },
            transitions = parts.flatMap { it.tr() },
            initialMarking = Marking(start),
            finalMarking = Marking(end),
        )
    }

    /**
     * A Petri net from Fig. 5 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig5: PetriNet = petrinet {
        P tout "A"
        P tin "A" tout "B" * "C"
        P tin "B" tout "E"
        P tin "E" * "F" tout "I"
        P tin "B" * "C" tout "D"
        P tin "D" tout "E" * "F"
        P tin "C" tout "_tauG" * "G"
        P tin "_tauG" * "G" tout "F"
        P tin "C" tout "_tauH" * "H"
        P tin "_tauH" * "H" tout "F"
        P tin "I"
    }

    /**
     * A Petri net from Fig. 6 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig6: PetriNet = petrinet {
        P tout "A"
        P tin "A" tout "B" * "C"
        P tin "B" tout "E"
        P tin "E" * "F" tout "I"
        P tin "B" * "C" tout "D"
        P tin "D" tout "E" * "F"
        P tin "C" * "F" * "G" * "H" tout "F" * "G" * "H"
        P tin "I"
    }

    /**
     * A Petri net from Fig. 7 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig7: PetriNet = petrinet {
        P tout "A"
        P tin "A" tout "B" * "C"
        P tin "B" tout "E"
        P tin "E" * "F" tout "I"
        P tin "B" * "C" * "D" tout "D" * "E" * "F"
        P tin "C" tout "_tauG" * "G"
        P tin "_tauG" * "G" tout "H"
        P tin "H" tout "F"
        P tin "I"
    }

    /**
     * A Petri net from Fig. 8 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig8: PetriNet = petrinet {
        P tout "_tau1"
        P tin "_tau1" tout "A" * "B" * "C" * "D" * "E" * "F" * "G" * "H" * "I"
        P tin "A" * "B" * "C" * "D" * "E" * "F" * "G" * "H" * "I" tout "_tau2"
        P tin "_tau2"
    }

    /**
     * A Petri net from Fig. 11 in B.F. van Dongen, A Unified Approach for Measuring Precision and Generalization Based
     * on Anti-Alignments.
     */
    val fig11: PetriNet = petrinet {
        P tout "A"
        P tin "A" * "C" tout "B" * "C" * "_tau1"
        P tin "B" tout "E"
        P tin "E" * "F" * "_tau3" tout "F" * "I"
        P tin "B" * "_tau1" tout "D"
        P tin "D" tout "E" * "_tau3"
        P tin "_tau1" tout "_tau2" * "G"
        P tin "_tau2" * "G" tout "H"
        P tin "H" tout "_tau3"
        P tin "C" tout "F"
        P tin "I"
    }
}
