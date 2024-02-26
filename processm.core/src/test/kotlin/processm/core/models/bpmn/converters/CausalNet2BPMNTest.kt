package processm.core.models.bpmn.converters

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.helpers.mapToSet
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CausalNet2BPMNTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")

    private fun compare(inp: CausalNet) {
        val bpmn = inp.toBPMN()
        val reconstructed = MutableCausalNet()
        reconstructed.copyFrom(bpmn.toCausalNet()) {
            if (it in inp.instances)
                return@copyFrom it
            else
                return@copyFrom Node(it.name, it.instanceId, true)
        }
        val ve = CausalNetVerifierImpl(inp)
        check(ve.isSound) { "Reference causal net in compare is unsound." }
        val expected = ve.validLoopFreeSequences.mapToSet { seq -> seq.map { it.a }.filterNot { it.isSilent } }
        val actual = CausalNetVerifierImpl(reconstructed).validLoopFreeSequences.mapToSet { seq ->
            seq.map { it.a }.filterNot { it.isSilent }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `parallel split parallel join`() {
        compare(causalnet {
            start = a
            end = d
            a splits b + c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c join d
        })
    }

    @Test
    fun linear() {
        compare(causalnet {
            start = a
            end = d
            a splits b
            b splits c
            c splits d
            a joins b
            b joins c
            c joins d
        })
    }

    @Test
    fun `parallel or exclusive split parallel or exclusive join`() {
        compare(causalnet {
            start = a
            end = d
            a splits b + c or c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c or c join d
        })
    }

    @Test
    fun `parallel or exclusive split parallel or exclusive join with names`() {
        val orig = causalnet {
            start = a
            end = d
            a splits b + c or c
            b splits d
            c splits d
            a joins b
            a joins c
            b + c or c join d
        }
        val c1 = orig.toBPMN(true).toCausalNet()
        val c2 = orig.toBPMN(false).toCausalNet()
        assertNotNull(c1.isomorphic(c2, emptyMap()))
    }

    @Ignore("Comparator is broken")
    @Test
    fun `self loop`() {
        compare(causalnet {
            start = a
            end = c
            a splits b
            b splits b or c
            a or b join b
            b joins c
        })
    }

}
