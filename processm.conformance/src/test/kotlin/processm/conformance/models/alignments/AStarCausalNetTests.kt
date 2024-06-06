package processm.conformance.models.alignments

import processm.conformance.alignment
import processm.conformance.models.DeviationType
import processm.core.log.Helpers
import processm.core.models.causalnet.CausalNets.azFlower
import processm.core.models.causalnet.CausalNets.fig312
import processm.core.models.causalnet.CausalNets.fig316
import processm.core.models.causalnet.CausalNets.parallelDecisionsInLoop
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.helpers.allSubsets
import processm.helpers.mapToSet
import processm.helpers.zipOrThrow
import processm.logging.loggedScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AStarCausalNetTests {
    @Test
    fun `PM book Fig 3 12 conforming log`() = loggedScope { logger ->
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

        val astar = AStar(fig312)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            logger.info("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)

            for ((step, event) in alignment.steps.asSequence() zipOrThrow trace.events) {
                assertEquals(event, step.logMove)
                assertEquals(DeviationType.None, step.type)
                assertEquals(event.conceptName, step.modelMove!!.name)
                assertTrue(step.modelMove?.name == "a" || step.modelCause.isNotEmpty())
            }
        }
    }

    @Test
    fun `PM book Fig 3 12 non-conforming log`() = loggedScope { logger ->
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

        val expected = arrayOf(
            arrayOf( // alternatives
                alignment {//  [a, b, log only: c, d, e, g, z]
                    "a" executing "a"
                    "b" executing ("b" to listOf("a"))
                    "c" executing null
                    "d" executing ("d" to listOf("a"))
                    "e" executing ("e" to listOf("b", "d"))
                    "g" executing ("g" to listOf("e"))
                    "z" executing ("z" to listOf("g"))
                    cost = 1
                },
                alignment {// [a, log only: b, c, d, e, g, z]
                    "a" executing "a"
                    "b" executing null
                    "c" executing ("c" to listOf("a"))
                    "d" executing ("d" to listOf("a"))
                    "e" executing ("e" to listOf("c", "d"))
                    "g" executing ("g" to listOf("e"))
                    "z" executing ("z" to listOf("g"))
                    cost = 1
                }),
            arrayOf(alignment {// [a, d, b, e, g, model only: z]
                "a" executing "a"
                "d" executing ("d" to listOf("a"))
                "b" executing ("b" to listOf("a"))
                "e" executing ("e" to listOf("b", "d"))
                "g" executing ("g" to listOf("e"))
                null executing ("z" to listOf("g"))
                cost = 1
            }),
            arrayOf(alignment {// [a, c, d, e, g, z, log only: x]
                "a" executing "a"
                "c" executing ("c" to listOf("a"))
                "d" executing ("d" to listOf("a"))
                "e" executing ("e" to listOf("c", "d"))
                "g" executing ("g" to listOf("e"))
                "z" executing ("z" to listOf("g"))
                "x" executing null
                cost = 1
            }),
            arrayOf(alignment {// [a, d, c, e, g, log only: x, z]
                "a" executing "a"
                "d" executing ("d" to listOf("a"))
                "c" executing ("c" to listOf("a"))
                "e" executing ("e" to listOf("c", "d"))
                "g" executing ("g" to listOf("e"))
                "x" executing null
                "z" executing ("z" to listOf("g"))
                cost = 1
            }),
            arrayOf(alignment {// [a, d, c, e, model only: f, b, d, e, g, z]
                "a" executing "a"
                "d" executing ("d" to listOf("a"))
                "c" executing ("c" to listOf("a"))
                "e" executing ("e" to listOf("c", "d"))
                null executing ("f" to listOf("e"))
                "b" executing ("b" to listOf("f"))
                "d" executing ("d" to listOf("f"))
                "e" executing ("e" to listOf("b", "d"))
                "g" executing ("g" to listOf("e"))
                "z" executing ("z" to listOf("g"))
                cost = 1
            }),
            arrayOf(alignment {// [a, d, c, e, model only: f, b, d, e, h, z]
                "a" executing "a"
                "d" executing ("d" to listOf("a"))
                "c" executing ("c" to listOf("a"))
                "e" executing ("e" to listOf("c", "d"))
                null executing ("f" to listOf("e"))
                "b" executing ("b" to listOf("f"))
                "d" executing ("d" to listOf("f"))
                "e" executing ("e" to listOf("b", "d"))
                "h" executing ("h" to listOf("e"))
                "z" executing ("z" to listOf("h"))
                cost = 1
            }),
        )

        val astar = AStar(fig312)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            logger.info("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")


            val results = expected[i].map { expected ->
                runCatching {
                    assertEquals(expected.cost, alignment.cost)
                    assertEquals(expected.steps.size, alignment.steps.size)
                    for ((exp, act) in expected.steps zip alignment.steps) {
                        assertEquals(exp.type, act.type)
                        assertEquals(exp.logMove?.conceptName, act.logMove?.conceptName)
                        assertEquals(exp.modelMove?.name, act.modelMove?.name)
                        assertEquals(exp.modelCause.mapToSet { it.name }, act.modelCause.mapToSet { it.name })
                    }
                }
            }

            if (results.none { it.isSuccess }) {
                results.forEach { it.getOrThrow() }
            }
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

        val astar = AStar(fig316)
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

        val expectedVisitedStatesCount = arrayOf(10, 20, 23, 77)

        val astar = AStar(fig316)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost} #visited states ${astar.visitedStatesCount}")

            assertEquals(expectedCost[i], alignment.cost)
            assertEquals(expectedVisitedStatesCount[i], astar.visitedStatesCount)
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

        val astar = AStar(azFlower)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost} #visited states ${astar.visitedStatesCount}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 - 1, alignment.steps.size)
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

        val astar = AStar(parallelDecisionsInLoop)
        for (trace in log.traces) {
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

        val astar = AStar(parallelDecisionsInLoop)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    //@Ignore("Intended for manual execution due to high resource requirements")
    @Test
    fun `Parallel decisions in loop with many splits C-net non-conforming log`() {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", isSilent = true)
        val en = Node("end", isSilent = true)

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

        val astar = AStar(model)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }
}
