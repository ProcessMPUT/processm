package processm.conformance.measures.complexity

import processm.conformance.CausalNets
import processm.conformance.PetriNets
import processm.conformance.ProcessTrees
import processm.core.log.StandardLifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberOfActivitiesTests {

    @Test
    fun `the empty Petri net has 0 activities`() {
        assertEquals(0, NumberOfActivities(PetriNets.empty))
    }

    @Test
    fun `the number of activities in a nonempty Petri net equals the number of transitions`() {
        assertEquals(PetriNets.fig32.transitions.size, NumberOfActivities(PetriNets.fig32))
        assertEquals(PetriNets.fig34c.transitions.size, NumberOfActivities(PetriNets.fig34c))
        assertEquals(PetriNets.fig314.transitions.size, NumberOfActivities(PetriNets.fig314))
        assertEquals(PetriNets.fig624N3.transitions.size, NumberOfActivities(PetriNets.fig624N3))
        assertEquals(PetriNets.fig82N1.transitions.size, NumberOfActivities(PetriNets.fig82N1))
        assertEquals(PetriNets.fig82N2.transitions.size, NumberOfActivities(PetriNets.fig82N2))
        assertEquals(PetriNets.fig82N3.transitions.size, NumberOfActivities(PetriNets.fig82N3))
        assertEquals(PetriNets.azFlower.transitions.size, NumberOfActivities(PetriNets.azFlower))
        assertEquals(PetriNets.parallelFlowers.transitions.size, NumberOfActivities(PetriNets.parallelFlowers))
    }

    @Test
    fun `silent activities in a Petri net count`() {
        assertTrue(StandardLifecycle.transitions.any { it.isSilent })
        assertEquals(StandardLifecycle.transitions.size, NumberOfActivities(StandardLifecycle))
    }

    @Test
    fun `duplicate activities in a Petri net count`() {
        assertEquals(2, NumberOfActivities(PetriNets.duplicateA))
    }

    @Test
    fun `the empty Causal net has only start and end activities`() {
        assertEquals(2, NumberOfActivities(CausalNets.empty))
    }

    @Test
    fun `the number of activities in a nonempty causal net is correct`() {
        assertEquals(CausalNets.fig312.activities.count(), NumberOfActivities(CausalNets.fig312))
        assertEquals(CausalNets.fig316.activities.count(), NumberOfActivities(CausalNets.fig316))
        assertEquals(CausalNets.azFlower.activities.count(), NumberOfActivities(CausalNets.azFlower))
        assertEquals(
            CausalNets.parallelDecisionsInLoop.activities.count(),
            NumberOfActivities(CausalNets.parallelDecisionsInLoop)
        )
    }

    @Test
    fun `silent activities in a causal net count`() {
        assertTrue(CausalNets.azFlower.activities.any { it.isSilent })
        assertEquals(CausalNets.azFlower.activities.count(), NumberOfActivities(CausalNets.azFlower))
    }

    @Test
    fun `duplicate activities in a causal net count`() {
        assertEquals(4, NumberOfActivities(CausalNets.duplicateA))
    }

    @Test
    fun `the empty process tree has 0 activities`() {
        assertEquals(0, NumberOfActivities(ProcessTrees.empty))
    }

    @Test
    fun `the number of activities in a nonempty process tree equals the number of leafs`() {
        assertEquals(
            ProcessTrees.fig727.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.fig727)
        )
        assertEquals(
            ProcessTrees.fig729.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.fig729)
        )
        assertEquals(
            ProcessTrees.azFlower.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.azFlower)
        )
        assertEquals(
            ProcessTrees.parallelFlowers.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.parallelFlowers)
        )
        assertEquals(
            ProcessTrees.parallelDecisionsInLoop.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.parallelDecisionsInLoop)
        )
    }

    @Test
    fun `silent activities in a process tree count`() {
        assertTrue(ProcessTrees.azFlower.activities.any { it.isSilent })
        assertEquals(
            ProcessTrees.azFlower.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.azFlower)
        )
        assertTrue(ProcessTrees.parallelFlowers.activities.any { it.isSilent })
        assertEquals(
            ProcessTrees.parallelFlowers.allNodes.count { it.children.isEmpty() },
            NumberOfActivities(ProcessTrees.parallelFlowers)
        )
    }

    @Test
    fun `duplicate activities in a process tree count`() {
        assertEquals(2, NumberOfActivities(ProcessTrees.duplicateA))
    }
}
