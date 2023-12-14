package processm.conformance.measures.complexity

import processm.conformance.PetriNets
import processm.core.models.causalnet.CausalNets
import processm.core.models.processtree.ProcessTrees
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlFlowComplexityTests {
    @Test
    fun `CFC equals 0 for the empty Petri net`() {
        assertEquals(0, CFC(PetriNets.empty))
    }

    @Test
    fun `CFC equals 17 for Petri net fig32`() {
        val XORsplits = 2 + 3
        val XORjoins = 2 + 2 + 2 + 2
        val ANDsplits = 1 + 1
        val ANDjoins = 1
        val causalities = 1
        val total = XORsplits + XORjoins + ANDsplits + ANDjoins + causalities
        assertEquals(total, CFC(PetriNets.fig32))
    }

    @Test
    fun `CFC equals 40 for Petri net fig314`() {
        val XORsplits = 5 + 3 + 3 + 3
        val XORjoins = 3 + 3 + 3 + 5
        val ANDsplits = 1 + 1 + 1
        val ANDjoins = 1 + 1 + 1
        val causalities = 6 * 1
        val total = XORsplits + XORjoins + ANDsplits + ANDjoins + causalities
        assertEquals(total, CFC(PetriNets.fig314))
    }

    @Test
    fun `CFC equals 0 for the empty causal net`() {
        assertEquals(0, CFC(CausalNets.empty))
    }

    @Test
    fun `CFC equals 25 for causal net fig312`() {
        val XORsplits = 3
        val XORjoins = 2 + 2 + 2 + 2
        val ANDsplits = 0
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 2 + 2
        val otherJoins = 2
        val causalities = 8
        val total = XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits + otherJoins +
                causalities
        assertEquals(total, CFC(CausalNets.fig312))
    }

    @Test
    fun `CFC equals 12 for causal net fig316`() {
        val XORsplits = 2
        val XORjoins = 2
        val ANDsplits = 0
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 2
        val otherJoins = 2
        val causalities = 4
        val total = XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits + otherJoins +
                causalities
        assertEquals(total, CFC(CausalNets.fig316))
    }

    @Test
    fun `CFC equals 0 for the empty process tree`() {
        assertEquals(0, CFC(ProcessTrees.empty))
    }

    @Test
    fun `CFC equals 9 for process tree fig727`() {
        val XORsplits = 2 + 2
        val XORjoins = 0
        val ANDsplits = 1
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 2
        val otherJoins = 0
        val causalities = 2
        val total = XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits + otherJoins +
                causalities
        assertEquals(total, CFC(ProcessTrees.fig727))
    }
}
