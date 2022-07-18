package processm.conformance.models.alignments

import processm.conformance.CausalNets.parallelDecisionsInLoop
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.helpers.SameThreadExecutorService
import processm.core.helpers.allSubsets
import processm.core.log.Helpers.logFromString
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.PetriNet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CausalNetAsPetriNetAlignerTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")


    @Test
    fun test() {
        val model = causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        }
        val factory = CausalNetAsPetriNetAlignerFactory { model, penalty, _ ->
            AStar(model, penalty)
        }
        val aligner = factory(model, PenaltyFunction(), SameThreadExecutorService)
        val alignment = aligner.align(logFromString("""
            a d
        """.trimIndent()))
        println(alignment.single())
    }

    @Test
    fun `Parallel flowers in loop C-net non-conforming log`() {
        val log = logFromString(
            """
                ls d1 d2 A N ls le ls d1 C d2 O le ls d1 D d2 P le ls d2 d1 E Q le ls d1 d2 F R le ls d2 d1 G S le ls d1 H d2 T le ls d1 I d2 U le ls d2 d1 J V le ls d1 d2 K W le ls d1 L d2 X ls le d1 M d2 Y le
            """
        )

        val expectedCost = arrayOf(
            3,
        )

        val factory = CausalNetAsPetriNetAlignerFactory { model, penalty, _ ->
            AStar(model, penalty)
        }
        val aligner = factory(parallelDecisionsInLoop, PenaltyFunction(), SameThreadExecutorService)
        for ((i, trace) in log.traces.withIndex()) {
            val start = System.currentTimeMillis()
            val alignment = aligner.align(trace)
            val time = System.currentTimeMillis() - start

            println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

            assertEquals(expectedCost[i], alignment.cost)

            assertTrue { alignment.steps.all { step -> step.modelMove === null || step.modelMove is DecoupledNodeExecution } }
        }
    }


    @Test
    fun `return alignments for a correct model - AStar`() {
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

        val log = logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le
            """
        )

        val factory = CausalNetAsPetriNetAlignerFactory { model, penalty, _ ->
            AStar(model, penalty)
        }
        val aligner = factory(model, PenaltyFunction(), SameThreadExecutorService)
        val alignments =aligner.align(log)
        assertTrue { alignments.all { alignment -> alignment.steps.all { step -> step.modelMove is DecoupledNodeExecution? } } }
    }

    @Test
    fun `return alignments for a correct model - DecompositionAligner`() {
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

        val log = logFromString(
            """
                ls d2 M d1 Z le
                d2 ls d1 Z M le
                ls d1 d2 A N ls le ls d1 C d2 O le
            """
        )

        val factory = CausalNetAsPetriNetAlignerFactory { model, penalty, _ ->
            DecompositionAligner(model as PetriNet, penalty)
        }
        val aligner = factory(model, PenaltyFunction(), SameThreadExecutorService)
        val alignments =aligner.align(log)
        assertTrue { alignments.all { alignment -> alignment.steps.all { step -> step.modelMove is DecoupledNodeExecution? } } }
    }
}
