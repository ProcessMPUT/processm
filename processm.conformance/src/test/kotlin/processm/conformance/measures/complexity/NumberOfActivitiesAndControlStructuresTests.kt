package processm.conformance.measures.complexity

import processm.conformance.PetriNets
import processm.core.models.causalnet.CausalNets
import processm.core.models.processtree.ProcessTrees
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberOfActivitiesAndControlStructuresTests {
    @Test
    fun `NOAC equals 0 for the empty Petri net`() {
        assertEquals(0, NOAC(PetriNets.empty))
    }

    @Test
    fun `NOAC equals 18 for Petri net fig32`() {
        val activities = 8
        val XORsplits = 2
        val XORjoins = 4
        val ANDsplits = 2
        val ANDjoins = 1
        val causalities = 1
        val total = activities + XORsplits + XORjoins + ANDsplits + ANDjoins + causalities
        assertEquals(total, NOAC(PetriNets.fig32))
    }

    @Test
    fun `NOAC equals 18 for Petri net fig314`() {
        val activities = 5 + 16 // labeled + silent
        val XORsplits = 4
        val XORjoins = 4
        val ANDsplits = 3
        val ANDjoins = 3
        val causalities = 6
        val total = activities + XORsplits + XORjoins + ANDsplits + ANDjoins + causalities
        assertEquals(total, NOAC(PetriNets.fig314))
    }

    @Test
    fun `NOAC equals 2 for the empty causal net`() {
        assertEquals(2, NOAC(CausalNets.empty))
    }

    @Test
    fun `NOAC equals 25 for causal net fig312`() {
        val activities = 9
        val XORsplits = 1
        val XORjoins = 4
        val ANDsplits = 0
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 2
        val otherJoins = 1
        val causalities = 8
        val total = activities + XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits +
                otherJoins + causalities
        assertEquals(total, NOAC(CausalNets.fig312))
    }

    @Test
    fun `NOAC equals 13 for causal net fig316`() {
        val activities = 5
        val XORsplits = 1
        val XORjoins = 1
        val ANDsplits = 0
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 1
        val otherJoins = 1
        val causalities = 4
        val total = activities + XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits +
                otherJoins + causalities
        assertEquals(total, NOAC(CausalNets.fig316))
    }

    @Test
    fun `NOAC equals 0 for the empty process tree`() {
        assertEquals(0, NOAC(ProcessTrees.empty))
    }

    @Test
    fun `NOAC equals 14 for process tree fig727`() {
        val activities = 8
        val XORsplits = 2
        val XORjoins = 0
        val ANDsplits = 1
        val ANDjoins = 0
        val ORsplits = 0
        val ORjoins = 0
        val otherSplits = 1
        val otherJoins = 0
        val causalities = 2
        val total = activities + XORsplits + XORjoins + ANDsplits + ANDjoins + ORsplits + ORjoins + otherSplits +
                otherJoins + causalities
        assertEquals(total, NOAC(ProcessTrees.fig727))
    }
}
