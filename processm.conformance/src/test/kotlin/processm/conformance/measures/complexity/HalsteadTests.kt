package processm.conformance.measures.complexity

import processm.conformance.CausalNets
import processm.conformance.PetriNets
import processm.core.models.processtree.ProcessTrees
import kotlin.test.Test
import kotlin.test.assertEquals

class HalsteadTests {
    @Test
    fun `the empty Petri net`() {
        val halstead = Halstead(PetriNets.empty)
        assertEquals(0, halstead.uniqueOperators)
        assertEquals(0, halstead.uniqueOperands)
        assertEquals(0, halstead.totalOperators)
        assertEquals(0, halstead.totalOperands)
        assertEquals(0.0, halstead.length)
        assertEquals(0.0, halstead.volume)
        assertEquals(0.0, halstead.difficulty)
        assertEquals(0.0, halstead.effort)
    }

    @Test
    fun fig32() {
        val halstead = Halstead(PetriNets.fig32)
        assertEquals(18, halstead.uniqueOperators)
        assertEquals(8, halstead.uniqueOperands)
        assertEquals(18, halstead.totalOperators)
        assertEquals(8, halstead.totalOperands)
        assertEquals(99.05866, halstead.length, 1e-5)
        assertEquals(122.21143, halstead.volume, 1e-5)
        assertEquals(9.0, halstead.difficulty)
        assertEquals(1099.90289, halstead.effort, 1e-5)
    }

    @Test
    fun `the empty causal net`() {
        val halstead = Halstead(CausalNets.empty)
        assertEquals(2, halstead.uniqueOperators)
        assertEquals(2, halstead.uniqueOperands)
        assertEquals(2, halstead.totalOperators)
        assertEquals(2, halstead.totalOperands)
        assertEquals(4.0, halstead.length)
        assertEquals(8.0, halstead.volume)
        assertEquals(1.0, halstead.difficulty)
        assertEquals(8.0, halstead.effort)
    }

    @Test
    fun fig312() {
        val halstead = Halstead(CausalNets.fig312)
        assertEquals(25, halstead.uniqueOperators)
        assertEquals(9, halstead.uniqueOperands)
        assertEquals(25, halstead.totalOperators)
        assertEquals(9, halstead.totalOperands)
        assertEquals(144.62573, halstead.length, 1e-5)
        assertEquals(172.97374, halstead.volume, 1e-5)
        assertEquals(12.5, halstead.difficulty)
        assertEquals(2162.17171, halstead.effort, 1e-5)
    }

    @Test
    fun `the empty process tree`() {
        val halstead = Halstead(ProcessTrees.empty)
        assertEquals(0, halstead.uniqueOperators)
        assertEquals(0, halstead.uniqueOperands)
        assertEquals(0, halstead.totalOperators)
        assertEquals(0, halstead.totalOperands)
        assertEquals(0.0, halstead.length)
        assertEquals(0.0, halstead.volume)
        assertEquals(0.0, halstead.difficulty)
        assertEquals(0.0, halstead.effort)
    }

    @Test
    fun fig727() {
        val halstead = Halstead(ProcessTrees.fig727)
        assertEquals(14, halstead.uniqueOperators)
        assertEquals(8, halstead.uniqueOperands)
        assertEquals(14, halstead.totalOperators)
        assertEquals(8, halstead.totalOperands)
        assertEquals(77.30297, halstead.length, 1e-5)
        assertEquals(98.10750, halstead.volume, 1e-5)
        assertEquals(7.0, halstead.difficulty)
        assertEquals(686.75247, halstead.effort, 1e-5)
    }
}
