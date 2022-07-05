package processm.conformance.models.alignments

import processm.conformance.CausalNets.azFlower
import processm.conformance.CausalNets.fig312
import processm.conformance.CausalNets.fig316
import processm.conformance.CausalNets.parallelDecisionsInLoop
import processm.core.helpers.allSubsets
import processm.core.log.Helpers
import processm.core.log.Helpers.trace
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.CausalNet2PetriNet
import processm.core.models.petrinet.converters.toPetriNet
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AStarCausalNetAsPetriNetTests {
    @Test
    fun `PM book Fig 3 12 conforming log`() {
        val log = Helpers.logFromString(
            """
                a b d e g z
                a d b e g z
                a c d e g z
                a d c e g z
                a d c e f b d e g z
                a d c e f b d e h z
                """
        )

        val petri = fig312.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 12 non-conforming log`() {
        val log = Helpers.logFromString(
            """
                a b c d e g z
                a d b e g
                a c d e g z x
                a d c e g x z
                a d c e b d e g z
                a d c e b d e h z
                """
        )

        val expectedCost = arrayOf(
            1,
            1,
            1,
            1,
            1,
            1
        )

        val petri = fig312.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 16 conforming log`() {
        val log = Helpers.logFromString(
            """
                a b c d e
                a b c b d c d e
                a b b c c d d e
                a b c b c b c b c d d d d e
                """
        )

        val petri = fig316.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 3 16 non-conforming log`() {
        val log = Helpers.logFromString(
            """
                a b d e
                a b c b d d e
                a b b c d d e
                a b c b c b d b c d d d e
                """
        )

        val expectedCost = arrayOf(
            1,
            1,
            1,
            2
        )

        val petri = fig316.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    @Test
    fun `Flower C-net`() {
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                A Y X W V U T S R Q P O N M L K J I H G F E D C B Z
                A B B B B B B B B B B B B B B B B B B B B B B B B Z
                A Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Y Z
                A Z
            """
        )

        val petri = azFlower.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(
                trace.events.count(),
                alignment.steps.count { it.modelMove === null || !it.modelMove!!.isSilent })
        }
    }

    @Test
    fun `Parallel decisions in loop C-net conforming log`() {
        val log = Helpers.logFromString(
            """
                ls d1 M d2 Z le
                ls d1 d2 A N le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X le ls d1 M d2 Y le
            """
        )

        val petri = parallelDecisionsInLoop.toPetriNet()
        val astar = AStar(petri)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `Parallel decisions in loop C-net non-conforming log`() {
        val log = Helpers.logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X ls le d1 M d2 Y le
            """
        )

        val expectedCost = arrayOf(
            2,
            2,
            3,
        )

        val converter = CausalNet2PetriNet(parallelDecisionsInLoop)
        val petri = converter.toPetriNet()
        val astar = CausalNetAsPetriNetAligner(AStar(petri), converter)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)

            assertTrue { alignment.steps.all { step -> step.modelMove === null || step.modelMove is DecoupledNodeExecution } }
        }
    }

    @Ignore("Intended for manual execution due to high resource requirements")
    @Test
    fun `Parallel decisions in loop with many splits C-net non-conforming log`() {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", isArtificial = true)
        val en = Node("end", isArtificial = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        val model = causalnet {
            start = st
            end = en

            st splits loopStart
            st joins loopStart
            loopStart splits dec1 + dec2

            loopStart joins dec1
            for (act1 in activities1) {
                dec1 joins act1
                act1 splits loopEnd
                for (act2 in activities2) {
                    act1 + act2 join loopEnd
                }
            }

            for (act1 in activities1.allSubsets(true).filter { it.size <= 3 }) {
                dec1 splits act1
            }

            loopStart joins dec2
            for (act2 in activities2) {
                dec2 splits act2
                dec2 joins act2
                act2 splits loopEnd
            }

            for (act2 in activities1.allSubsets(true).filter { it.size <= 3 }) {
                dec2 splits act2
            }

            loopEnd splits loopStart
            loopEnd joins loopStart

            loopEnd splits en
            loopEnd joins en
        }

        val log = Helpers.logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le
            """
            //  ls d1 d2 A N ls le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X ls le d1 M d2 Y le
        )

        val expectedCost = arrayOf(
            2,
            2,
            1,
        )

        val converter = CausalNet2PetriNet(model)
        val petri = converter.toPetriNet()
        val astar = CausalNetAsPetriNetAligner(AStar(petri), converter)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)

            assertTrue { alignment.steps.all { step -> step.modelMove === null || step.modelMove is DecoupledNodeExecution } }
        }
    }

    @Test
    fun `multiple instances with the same name with singular bindings`() {
        val a1 = Node("a", "1")
        val a2 = Node("a", "2")
        val a3 = Node("a", "3")
        val model = causalnet {
            start splits a1 or a2 or a3
            a1 splits end
            a2 splits end
            a3 splits end
            start joins a1
            start joins a2
            start joins a3
            a1 or a2 or a3 join end
        }
        val converter = CausalNet2PetriNet(model)
        val petri = converter.toPetriNet()
        val astar = CausalNetAsPetriNetAligner(AStar(petri), converter)
        val alignment = astar.align(trace(a1))
        assertEquals(0, alignment.cost)
        assertEquals(3, alignment.steps.size)
    }

    @Test
    fun `instanceId uniquely determined by surrounding activities`() {
        val a1=Node("a1")
        val a2=Node("a2")
        val b1=Node("b","1")
        val b2=Node("b", "2")
        val c1=Node("c1")
        val c2=Node("c2")
        val model = causalnet {
            start splits a1 or a2
            a1 splits b1
            a2 splits b2
            b1 splits c1
            b2 splits c2
            c1 splits end
            c2 splits end
            start joins a1
            start joins a2
            a1 joins b1
            a2 joins b2
            b1 joins c1
            b2 joins c2
            c1 or c2 join end
        }
        val converter = CausalNet2PetriNet(model)
        val petri = converter.toPetriNet()
        println(petri.toMultilineString())
        val astar = CausalNetAsPetriNetAligner(AStar(petri), converter)
        val alignment = astar.align(trace(a1, b1, c1))
        assertEquals(0, alignment.cost)
        assertEquals(5, alignment.steps.size)
    }
}
