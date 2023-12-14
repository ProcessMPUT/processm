package processm.conformance.measures.complexity

import processm.conformance.PetriNets
import processm.core.log.StandardLifecycle
import processm.core.models.causalnet.CausalNets
import processm.core.models.processtree.ProcessTrees
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberOfActivitiesTests {

    @Test
    fun `the empty Petri net has 0 activities`() {
        assertEquals(0, NOA(PetriNets.empty))
    }

    @Test
    fun `the number of activities in a nonempty Petri net equals the number of transitions`() {
        assertEquals(PetriNets.fig32.transitions.size, NOA(PetriNets.fig32))
        assertEquals(PetriNets.fig34c.transitions.size, NOA(PetriNets.fig34c))
        assertEquals(PetriNets.fig314.transitions.size, NOA(PetriNets.fig314))
        assertEquals(PetriNets.fig624N3.transitions.size, NOA(PetriNets.fig624N3))
        assertEquals(PetriNets.fig82N1.transitions.size, NOA(PetriNets.fig82N1))
        assertEquals(PetriNets.fig82N2.transitions.size, NOA(PetriNets.fig82N2))
        assertEquals(PetriNets.fig82N3.transitions.size, NOA(PetriNets.fig82N3))
        assertEquals(PetriNets.azFlower.transitions.size, NOA(PetriNets.azFlower))
        assertEquals(PetriNets.parallelFlowers.transitions.size, NOA(PetriNets.parallelFlowers))
    }

    @Test
    fun `silent activities in a Petri net count`() {
        assertTrue(StandardLifecycle.transitions.any { it.isSilent })
        assertEquals(StandardLifecycle.transitions.size, NOA(StandardLifecycle))
    }

    @Test
    fun `duplicate activities in a Petri net count`() {
        assertEquals(2, NOA(PetriNets.duplicateA))
    }

    @Test
    fun `the empty Causal net has only start and end activities`() {
        assertEquals(2, NOA(CausalNets.empty))
    }

    @Test
    fun `the number of activities in a nonempty causal net is correct`() {
        assertEquals(CausalNets.fig312.activities.count(), NOA(CausalNets.fig312))
        assertEquals(CausalNets.fig316.activities.count(), NOA(CausalNets.fig316))
        assertEquals(CausalNets.azFlower.activities.count(), NOA(CausalNets.azFlower))
        assertEquals(
            CausalNets.parallelDecisionsInLoop.activities.count(),
            NOA(CausalNets.parallelDecisionsInLoop)
        )
    }

    @Test
    fun `silent activities in a causal net count`() {
        assertTrue(CausalNets.azFlower.activities.any { it.isSilent })
        assertEquals(CausalNets.azFlower.activities.count(), NOA(CausalNets.azFlower))
    }

    @Test
    fun `duplicate activities in a causal net count`() {
        assertEquals(4, NOA(CausalNets.duplicateA))
    }

    @Test
    fun `the empty process tree has 0 activities`() {
        assertEquals(0, NOA(ProcessTrees.empty))
    }

    @Test
    fun `the number of activities in a nonempty process tree equals the number of leafs`() {
        assertEquals(
            ProcessTrees.fig727.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.fig727)
        )
        assertEquals(
            ProcessTrees.fig729.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.fig729)
        )
        assertEquals(
            ProcessTrees.azFlower.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.azFlower)
        )
        assertEquals(
            ProcessTrees.parallelFlowers.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.parallelFlowers)
        )
        assertEquals(
            ProcessTrees.parallelDecisionsInLoop.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.parallelDecisionsInLoop)
        )
    }

    @Test
    fun `silent activities in a process tree count`() {
        assertTrue(ProcessTrees.azFlower.activities.any { it.isSilent })
        assertEquals(
            ProcessTrees.azFlower.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.azFlower)
        )
        assertTrue(ProcessTrees.parallelFlowers.activities.any { it.isSilent })
        assertEquals(
            ProcessTrees.parallelFlowers.allNodes.count { it.children.isEmpty() },
            NOA(ProcessTrees.parallelFlowers)
        )
    }

    @Test
    fun `duplicate activities in a process tree count`() {
        assertEquals(2, NOA(ProcessTrees.duplicateA))
    }
}
