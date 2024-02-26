package processm.conformance.models.alignments

import processm.conformance.PetriNets.azFlower
import processm.conformance.PetriNets.fig314
import processm.conformance.PetriNets.fig32
import processm.conformance.PetriNets.fig34c
import processm.conformance.PetriNets.fig73
import processm.conformance.PetriNets.parallelFlowers
import processm.core.log.Helpers.event
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Trace
import processm.helpers.allPermutations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AStarPetriNetTests {
    @Test
    fun `PM book Fig 3 2 conforming log`() {
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

        val astar = AStar(fig32)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost} #visited states: ${astar.visitedStatesCount}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }
    }

    @Test
    fun `PM book Fig 3 2 non-conforming log`() {
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

        val expectedCosts = listOf(
            2,
            1,
            1,
            1,
            2,
            3,
            4
        )

        val expectedVisitedStatesCount = listOf(34, 6, 10, 16, 19, 16, 76)

        val astar = AStar(fig32)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost} #visited states: ${astar.visitedStatesCount}")

            assertEquals(expectedCosts[i], alignment.cost)
            assertTrue { astar.visitedStatesCount <= expectedVisitedStatesCount[i] }
        }
    }

    @Test
    fun `PM book Fig 3 4 c conforming log`() {
        val t1 = fig34c.transitions.first { it.name == "t1" }
        val t2 = fig34c.transitions.first { it.name == "t2" }
        val t3 = fig34c.transitions.first { it.name == "t3" }
        val t4 = fig34c.transitions.first { it.name == "t4" }
        val t5 = fig34c.transitions.first { it.name == "t5" }
        val allMoves = List(5) { t1 } + List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }

        val limit = 10000
        var totalTime: Long = 0L
        val astar = AStar(fig34c)
        for (activities in allMoves.allPermutations().take(limit)) {
            val trace = Trace(activities.asSequence().map { event(it.name) })
            val start = System.nanoTime()
            val alignment = astar.align(trace)
            val time = System.nanoTime() - start
            totalTime += time

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }

        totalTime /= 1000000L
        println("Total time: ${totalTime}ms, ${totalTime.toDouble() / limit}ms per alignment")
    }

    @Test
    fun `PM book Fig 3 4 c non-conforming log`() {
        val t2 = fig34c.transitions.first { it.name == "t2" }
        val t3 = fig34c.transitions.first { it.name == "t3" }
        val t4 = fig34c.transitions.first { it.name == "t4" }
        val t5 = fig34c.transitions.first { it.name == "t5" }
        // missing t1s
        val allMoves = List(5) { t2 } + List(5) { t3 } + List(5) { t4 } + List(5) { t5 }

        val limit = 100
        var totalTime: Long = 0L
        val astar = AStar(fig34c)
        for (activities in allMoves.allPermutations().take(limit)) {
            val trace = Trace(activities.asSequence().map { event(it.name) })
            val start = System.nanoTime()
            val alignment = astar.align(trace)
            val time = System.nanoTime() - start
            totalTime += time

            assertEquals(5, alignment.cost)
            assertEquals(25, alignment.steps.size)
        }

        totalTime /= 1000000L
        println("Total time: ${totalTime}ms, ${totalTime.toDouble() / limit}ms per alignment")
    }

    @Test
    fun `PM book Fig 3 14 conforming log`() {
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

        val astar = AStar(fig314)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.filter { it.logMove !== null }.size)
            for (step in alignment.steps)
                if (step.logMove !== null)
                    assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
                else
                    assertTrue(step.modelMove!!.isSilent)
        }
    }

    @Test
    fun `PM book Fig 3 14 non-conforming log`() {
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

        val expectedCosts = listOf(
            1,
            1,
            1,
            2,
            1,
            1,
            6
        )

        val astar = AStar(fig314)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 7 3 conforming log`() {
        val log = logFromString(
            """
                a b c e
                a c b e
                d a d b d c d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d e d d d d d d d
            """
        )

        val astar = AStar(fig73)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.filter { it.logMove !== null }.size)
            for (step in alignment.steps)
                if (step.logMove !== null)
                    assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
                else
                    assertTrue(step.modelMove!!.isSilent)
        }
    }

    @Test
    fun `PM book Fig 7 3 non-conforming log`() {
        val log = logFromString(
            """
                a b e
                a c e
                d a z d b d d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d d d d d d d d
                d a e d d b c e
            """
        )

        val expectedCosts = listOf(
            1,
            1,
            2,
            1,
            1
        )

        val astar = AStar(fig73)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `Flower model conforming log`() {
        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val astar = AStar(azFlower)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.filter { it.logMove !== null }.size)
            for (step in alignment.steps)
                if (step.logMove !== null)
                    assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
                else
                    assertTrue(step.modelMove!!.isSilent)
        }
    }

    @Test
    fun `Flower model non-conforming log`() {
        val log = logFromString(
            """
                1 a b c 5 d e f 09 g h i 13 j k l m n o p q r s t u w v x y z
                z 2 y x v 6 w u t 10 s r q 14 p o n m l k j i h g f e d c b a
                a a 3 a a a 7 a a a 11 a a a 15 a a z z z z z z z z z z z z z
                z z z 4 z z z 8 z z z 12 z z z 16 z a a a a a a a a a a a a a
            """
        )

        val astar = AStar(azFlower)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(4, alignment.cost)
        }
    }

    @Test
    fun `Parallel flower models in loop conforming log`() {
        val log = logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val astar = AStar(parallelFlowers)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.filter { it.logMove !== null }.size)
            for (step in alignment.steps)
                if (step.logMove !== null)
                    assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
                else
                    assertTrue(step.modelMove!!.isSilent)
        }
    }

    @Test
    fun `Parallel flower models in loop non-conforming log`() {
        val log = logFromString(
            """
                a a a a a a a a a a a a a z 1
                a b c 1 d e f g h i j k l m
                z y x v w u t s r q 1 p o n 2
                2 z z z z z z z z 1 z z z z a
            """
        )

        val expectedCosts = listOf(
            1,
            1,
            2,
            2
        )

        val astar = AStar(parallelFlowers)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = astar.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }
}
