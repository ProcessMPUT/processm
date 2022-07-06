package processm.conformance.models.alignments.processtree

import org.junit.jupiter.api.Test
import processm.conformance.ProcessTrees
import processm.core.log.Helpers
import kotlin.test.assertEquals

class DecompositionAlignerTests {
    @Test
    fun `PM book Fig 7 27 conforming log`() {
        val log = Helpers.logFromString(
            """
                A B D E H
                A D C E G
                A C D E F B D E G
                A D B E H
                A C D E F D C E F C D E H
                A C D E G
                A D C E F C D E F D B E F D C E F C D E F D B E H
                A B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E F B D E F C D E F D B E F D C E H
                """
        )

        val aligner = DecompositionAligner(ProcessTrees.fig727)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }
    }

    @Test
    fun `PM book Fig 7 27 non-conforming log`() {
        val log = Helpers.logFromString(
            """
                A E B D H
                D A C E G
                D A D C E G
                A C D E F D E G
                A D B E G H
                A C D Z E F D C E F C D E H
                A C E G
                A D C E F C D E F D B F D C E F C D E F D B E H
                H E D C A
                A B C D E F B C D E F D B E H
                A B D E F C D E F C D E F D B C E F D C B E F
                """
        )

        val expectedCosts = listOf(
            2,
            2,
            1,
            1,
            1,
            1,
            1,
            1,
            6,
            2,
            4
        )

        val aligner = DecompositionAligner(ProcessTrees.fig727)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.count { it.logMove !== null })
        }
    }

    @Test
    fun `Flower process tree`() {
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A
                Z
            """
        )

        val aligner = DecompositionAligner(ProcessTrees.azFlower)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 + 1, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel flower models`() {
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A
                Z
                Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z A A A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z Z Z Z Z Z Z Z Z Z Z Y Z Z Z Z Z Z Z Z Z Z Z Z A A A A A A A A A A A A B A A A A A A A A A A A A A
            """
        )

        val aligner = DecompositionAligner(ProcessTrees.parallelFlowers)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count() * 2 + 2, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel flower models non-conforming log`() {
        val log = Helpers.logFromString(
            """
                1 A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z 2 Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A 3 A A A A A A A A A A A A A A A A A A A A A A A A
                Z Z Z 4 Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z Z
                A 5
                Z 6
                Z Z Z 7 A A A
            """
        )

        val aligner = DecompositionAligner(ProcessTrees.parallelFlowers)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(1, alignment.cost)
            assertEquals(trace.events.count() * 2 + 1, alignment.steps.size)
        }
    }

    @Test
    fun `Parallel decisions in loop process tree`() {
        val log = Helpers.logFromString(
            """
                A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A Z Z A A Z Z A A Z
                A Z
                Z A
            """
        )

        val aligner = DecompositionAligner(ProcessTrees.parallelDecisionsInLoop)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
        }
    }

    @Test
    fun `Parallel decisions in loop non-conforming log`() {
        val log = Helpers.logFromString(
            """
                A A B C D E F G H I J K L M N O P Q R S T U V W X Y Z
                Z Z Y X W V U T S R Q P O N M L K J I H G F E D C B A
                A A Z Z Z
                Z Z A A A
                A Y Z B
                Z B A Y
                A A A
                Z Z Z
                Z Z A A A A Z Z
            """
        )

        val expectedCosts = listOf(
            1,
            1,
            3,
            3,
            2,
            2,
            3,
            3,
            4
        )

        val aligner = DecompositionAligner(ProcessTrees.parallelDecisionsInLoop)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 7 29 conforming log`() {
        val log = Helpers.logFromString(
            """
                A C E G
                A E C G
                B D F G
                B F D G
                """
        )

        val aligner = DecompositionAligner(ProcessTrees.fig729)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(0, alignment.cost)
            assertEquals(trace.events.count(), alignment.steps.size)
            for (step in alignment.steps)
                assertEquals(step.logMove!!.conceptName, step.modelMove!!.name)
        }
    }

    @Test
    fun `PM book Fig 7 29 non-conforming log`() {
        val log = Helpers.logFromString(
            """
                D F B G E C A
                """
        )

        val expectedCosts = listOf(
            5,
        )

        val aligner = DecompositionAligner(ProcessTrees.fig729)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }
}
