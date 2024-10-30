package processm.conformance.models.alignments

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import kotlin.test.assertEquals

class ExponentiallyLongValidBindingSequence {

    /**
     * This test showcases a model where the only(?) valid binding sequence is of exponential length.
     * The model has 3n activities whereas the sequence length is 3*2^n
     *
     * @param n The number of bits in the counter simulated by the model. Setting it to 5 seems to be too much for the available aligners.
     */
    @ParameterizedTest
    @ValueSource(ints = [2, 3, 4])
    fun testCounter(n: Int) {
        val cnet = buildCounter(n)
        val alignment = CompositeAligner(cnet).align(Trace(emptySequence()))
        val validBindingSequenceLength =
            alignment.steps.count { (it.modelMove as DecoupledNodeExecution?)?.activity !== null }
        assertEquals(3 * (1 shl n), validBindingSequenceLength)
    }

    fun buildCounter(n: Int): CausalNet {
        val zero = (0 until n).map { Node("!$it") }
        val one = (0 until n).map { Node("$it") }
        val ovrf = (0 until n).map { Node("$it ovrf") }
        val start = Node("start")
        val end = Node("end")
        return MutableCausalNet(start = start, end = end).apply {
            addInstance(*zero.toTypedArray())
            addInstance(*one.toTypedArray())
            addInstance(*ovrf.toTypedArray())
            addDependency(start, zero[0])
            // start
            (1 until n).forEach { addDependency(start, one[it]) }
            // within bit
            (0 until n).forEach {
                addDependency(zero[it], one[it])
                addDependency(one[it], zero[it])
                addDependency(one[it], ovrf[it])
            }
            // between bits
            (0 until n - 1).forEach {
                addDependency(ovrf[it], zero[it + 1])
                addDependency(ovrf[it], one[it + 1])
            }
            // end
            (0 until n).forEach {
                addDependency(zero[it], end)
            }
            addDependency(ovrf[n - 1], end)

            addSplit(Split(outgoing[start]!!))
            (0 until n).forEach {
                for (d in outgoing[zero[it]]!!)
                    addSplit(Split(setOf(d)))
                addSplit(Split(setOf(Dependency(one[it], zero[it])) + incoming[ovrf[it]]!!))
                for (d in outgoing[ovrf[it]]!!)
                    addSplit(Split(setOf(d)))
            }
            addJoin(Join(incoming[end]!!))
            for (d in incoming[zero[0]]!!)
                addJoin(Join(setOf(d)))
            addJoin(Join(incoming[one[0]]!!))
            ovrf.forEach {
                addJoin(Join(incoming[it]!!))
            }
            (1 until n).forEach {
                addJoin(Join(incoming[zero[it]]!!))
                addJoin(Join(setOf(Dependency(zero[it], one[it]), Dependency(ovrf[it - 1], one[it]))))
                addJoin(Join(setOf(Dependency(start, one[it]), Dependency(ovrf[it - 1], one[it]))))
            }
        }
    }

}