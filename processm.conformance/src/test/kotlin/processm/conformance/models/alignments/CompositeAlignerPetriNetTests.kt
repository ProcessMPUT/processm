package processm.conformance.models.alignments

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.assertThrows
import processm.conformance.CausalNets
import processm.conformance.CausalNets.fig312
import processm.conformance.CausalNets.fig316
import processm.conformance.PetriNets
import processm.conformance.PetriNets.fig314
import processm.conformance.PetriNets.fig32
import processm.conformance.PetriNets.fig34c
import processm.conformance.PetriNets.fig73
import processm.core.helpers.allPermutations
import processm.core.helpers.allSubsets
import processm.core.log.Helpers
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.toPetriNet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositeAlignerPetriNetTests {
    companion object {
        val pool = Executors.newCachedThreadPool()

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `PM book Fig 3 2 conforming log`() {
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(fig32, pool = pool)
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
    fun `PM book Fig 3 2 non-conforming log`() {
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(fig32, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
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
        val aligner = CompositeAligner(fig34c, pool = pool)
        for (activities in allMoves.allPermutations().take(limit)) {
            val trace = Trace(activities.asSequence().map { Helpers.event(it.name) })
            val start = System.nanoTime()
            val alignment = aligner.align(trace)
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
        val aligner = CompositeAligner(fig34c, pool = pool)
        for (activities in allMoves.allPermutations().take(limit)) {
            val trace = Trace(activities.asSequence().map { Helpers.event(it.name) })
            val start = System.nanoTime()
            val alignment = aligner.align(trace)
            val time = System.nanoTime() - start
            totalTime += time

            assertEquals(5, alignment.cost, "\n" + alignment.toStringMultiline())
            assertEquals(25, alignment.steps.size)
        }

        totalTime /= 1000000L
        println("Total time: ${totalTime}ms, ${totalTime.toDouble() / limit}ms per alignment")
    }

    @Test
    fun `PM book Fig 3 14 conforming log`() {
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(fig314, pool = pool)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(fig314, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `PM book Fig 7 3 conforming log`() {
        val log = Helpers.logFromString(
            """
                a b c e
                a c b e
                d a d b d c d e d
                d d d d d d a d d d d d d d c d d d d d d d b d d d d d d d e d d d d d d d
            """
        )

        val aligner = CompositeAligner(fig73, pool = pool)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(fig73, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

    @Test
    fun `Flower model conforming log`() {
        val log = Helpers.logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val aligner = CompositeAligner(PetriNets.azFlower, pool = pool)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val log = Helpers.logFromString(
            """
                1 a b c 5 d e f 09 g h i 13 j k l m n o p q r s t u w v x y z
                z 2 y x v 6 w u t 10 s r q 14 p o n m l k j i h g f e d c b a
                a a 3 a a a 7 a a a 11 a a a 15 a a z z z z z z z z z z z z z
                z z z 4 z z z 8 z z z 12 z z z 16 z a a a a a a a a a a a a a
            """
        )

        val aligner = CompositeAligner(PetriNets.azFlower, pool = pool)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(4, alignment.cost)
        }
    }

    @Test
    fun `Parallel flower models in loop conforming log`() {
        val log = Helpers.logFromString(
            """
                a b c d e f g h i j k l m n o p q r s t u w v x y z
                z y x v w u t s r q p o n m l k j i h g f e d c b a
                a a a a a a a a a a a a a z z z z z z z z z z z z z
                z z z z z z z z z z z z z a a a a a a a a a a a a a
            """
        )

        val aligner = CompositeAligner(PetriNets.parallelFlowers, pool = pool)
        for (trace in log.traces) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val log = Helpers.logFromString(
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

        val aligner = CompositeAligner(PetriNets.parallelFlowers, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCosts[i], alignment.cost)
        }
    }

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
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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

        val petri = CausalNets.azFlower.toPetriNet()
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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

        val petri = CausalNets.parallelDecisionsInLoop.toPetriNet()
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
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

        val petri = CausalNets.parallelDecisionsInLoop.toPetriNet()
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

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
            3
        )

        val petri = model.toPetriNet()
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)
        }
    }

    @Test
    fun `Parallel decisions in loop with many splits C-net non-conforming log - timeout`() {
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
        )

        val petri = model.toPetriNet()
        val aligner = CompositeAligner(petri, pool = pool)
        for ((i, trace) in log.traces.withIndex()) {
            assertThrows<IllegalStateException> { aligner.align(trace, 1, TimeUnit.MILLISECONDS) }
            assertNotNull(aligner.align(trace, 100, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `return alignments for a correct model`() {
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
        )

        val alignments = CompositeAligner(model, cache = null, pool = pool).align(log)
        assertTrue { alignments.all { alignment -> alignment.steps.all { step -> step.modelMove is DecoupledNodeExecution? } } }
    }
}
