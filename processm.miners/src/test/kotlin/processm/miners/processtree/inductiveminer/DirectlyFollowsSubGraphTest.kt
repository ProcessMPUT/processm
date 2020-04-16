package processm.miners.processtree.inductiveminer

import org.junit.jupiter.api.assertThrows
import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import kotlin.test.*

internal class DirectlyFollowsSubGraphTest {
    private fun activitiesSet(l: Collection<ProcessTreeActivity>) = HashSet<ProcessTreeActivity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if contains only one activity`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - more than one activity on graph`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B")
            )
        )
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - connection between activity`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arc ->
                arc[ProcessTreeActivity("A")] = Arc().increment()
                conn[ProcessTreeActivity("A")] = arc
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Fetch alone activity from graph`() {
        val activities = activitiesSet(listOf(ProcessTreeActivity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertEquals(ProcessTreeActivity("A"), graph.finishCalculations())
    }

    @Test
    fun `Exception if can't fetch activity`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B")
            )
        )
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertThrows<IllegalStateException> {
            graph.finishCalculations()
        }.also { exception ->
            assertEquals("SubGraph is not split yet. Can't fetch activity!", exception.message)
        }
    }

    @Test
    fun `Graph without separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]}, part G1
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val assignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        val expectedLabel = assignment[ProcessTreeActivity("A")]!!
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("B")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("C")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("D")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("E")])
    }

    @Test
    fun `Graph without separated parts but with not ordered activity`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
        }

        val assignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        val expectedLabel = assignment[ProcessTreeActivity("A")]!!
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("B")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("C")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("D")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("E")])
        assertEquals(expectedLabel, assignment[ProcessTreeActivity("F")])
    }

    @Test
    fun `Graph with separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]} part G1b
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
        }

        val assignment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        // B & C in group
        assertEquals(assignment[ProcessTreeActivity("B")], assignment[ProcessTreeActivity("C")])

        // E with different label
        assertNotEquals(assignment[ProcessTreeActivity("B")], assignment[ProcessTreeActivity("E")])
    }

    @Test
    fun `Split graph based on list of activities - all activity in the same group`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("E")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.splitIntoSubGraphs(graph.calculateExclusiveCut())

        assertEquals(1, result.size)
    }

    @Test
    fun `Split graph based on list of activities - separated groups`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.splitIntoSubGraphs(graph.calculateExclusiveCut())

        assertEquals(2, result.size)
    }

    @Test
    fun `Redo loop operator as default rule of subGraph cut`() {
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("C")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.finishWithDefaultRule()

        assertEquals("⟲(τ,A,B,C)", result.toString())
    }

    @Test
    fun `Strongly connected components`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F"),
                ProcessTreeActivity("G"),
                ProcessTreeActivity("H")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("H")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("F")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("G")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("H")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val result = graph.stronglyConnectedComponents()

        assertEquals(3, result.size)
        assertTrue(result.contains(setOf(ProcessTreeActivity("F"), ProcessTreeActivity("G"))))
        assertTrue(result.contains(setOf(ProcessTreeActivity("C"), ProcessTreeActivity("D"), ProcessTreeActivity("H"))))
        assertTrue(result.contains(setOf(ProcessTreeActivity("A"), ProcessTreeActivity("B"), ProcessTreeActivity("E"))))
    }

    @Test
    fun `Strongly connected components into connection matrix`() {
        // Based on https://en.wikipedia.org/wiki/Strongly_connected_component
        val activities = activitiesSet(
            listOf(
                ProcessTreeActivity("A"),
                ProcessTreeActivity("B"),
                ProcessTreeActivity("C"),
                ProcessTreeActivity("D"),
                ProcessTreeActivity("E"),
                ProcessTreeActivity("F"),
                ProcessTreeActivity("G"),
                ProcessTreeActivity("H")
            )
        )

        val connections = HashMap<ProcessTreeActivity, HashMap<ProcessTreeActivity, Arc>>().also { conn ->
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("B")] = Arc()
                conn[ProcessTreeActivity("A")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("E")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("B")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("C")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("C")] = Arc()
                arcs[ProcessTreeActivity("H")] = Arc()
                conn[ProcessTreeActivity("D")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("A")] = Arc()
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("E")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("F")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("F")] = Arc()
                conn[ProcessTreeActivity("G")] = arcs
            }
            HashMap<ProcessTreeActivity, Arc>().also { arcs ->
                arcs[ProcessTreeActivity("D")] = Arc()
                arcs[ProcessTreeActivity("G")] = Arc()
                conn[ProcessTreeActivity("H")] = arcs
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)
        val stronglyConnected = graph.stronglyConnectedComponents()
        val result = graph.connectionMatrix(stronglyConnected)

        assertEquals(3, result.size)
        assertEquals(0, result[0][0])
        assertEquals(1, result[0][1])
        assertEquals(1, result[0][2])
        assertEquals(-1, result[1][0])
        assertEquals(0, result[1][1])
        assertEquals(1, result[1][2])
        assertEquals(-1, result[2][0])
        assertEquals(-1, result[2][1])
        assertEquals(0, result[2][2])
    }
}