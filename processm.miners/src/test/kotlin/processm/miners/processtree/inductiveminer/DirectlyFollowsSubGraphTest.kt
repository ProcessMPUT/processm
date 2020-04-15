package processm.miners.processtree.inductiveminer

import org.junit.jupiter.api.assertThrows
import processm.core.models.processtree.Activity
import processm.core.models.processtree.SilentActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import kotlin.test.*

internal class DirectlyFollowsSubGraphTest {
    private fun activitiesSet(l: Collection<Activity>) = HashSet<Activity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if contains only one activity`() {
        val activities = activitiesSet(listOf(Activity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertTrue(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - more than one activity on graph`() {
        val activities = activitiesSet(
            listOf(
                Activity("A"),
                Activity("B")
            )
        )
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `NOT possible to finish calculations - connection between activity`() {
        val activities = activitiesSet(listOf(Activity("A")))
        val connections = HashMap<Activity, HashMap<Activity, Arc>>().also { conn ->
            HashMap<Activity, Arc>().also { arc ->
                arc[Activity("A")] = Arc().increment()
                conn[Activity("A")] = arc
            }
        }

        val graph = DirectlyFollowsSubGraph(activities, connections)

        assertFalse(graph.canFinishCalculationsOnSubGraph())
    }

    @Test
    fun `Fetch alone activity from graph`() {
        val activities = activitiesSet(listOf(Activity("A")))
        val graph = DirectlyFollowsSubGraph(activities, hashMapOf())

        assertEquals(Activity("A"), graph.finishCalculations())
    }

    @Test
    fun `Exception if can't fetch activity`() {
        val activities = activitiesSet(
            listOf(
                Activity("A"),
                Activity("B")
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
                Activity("A"),
                Activity("B"),
                Activity("C"),
                Activity("D"),
                Activity("E")
            )
        )

        val connections = HashMap<Activity, HashMap<Activity, Arc>>().also { conn ->
            // A -> B,C,E
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("B")] = Arc()
                arcs[Activity("C")] = Arc()
                arcs[Activity("E")] = Arc()
                conn[Activity("A")] = arcs
            }

            // B -> C, D
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("C")] = Arc()
                arcs[Activity("D")] = Arc()
                conn[Activity("B")] = arcs
            }

            // C -> B, D
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("B")] = Arc()
                arcs[Activity("D")] = Arc()
                conn[Activity("C")] = arcs
            }

            // E -> D
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("D")] = Arc()
                conn[Activity("E")] = arcs
            }
        }

        val assigment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        val expectedLabel = assigment[Activity("A")]!!
        assertEquals(expectedLabel, assigment[Activity("B")])
        assertEquals(expectedLabel, assigment[Activity("C")])
        assertEquals(expectedLabel, assigment[Activity("D")])
        assertEquals(expectedLabel, assigment[Activity("E")])
    }

    @Test
    fun `Graph without separated parts but with not ordered activity`() {
        val activities = activitiesSet(
            listOf(
                Activity("A"),
                Activity("B"),
                Activity("C"),
                Activity("D"),
                Activity("E"),
                Activity("F")
            )
        )

        val connections = HashMap<Activity, HashMap<Activity, Arc>>().also { conn ->
            // A -> B,C
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("B")] = Arc()
                arcs[Activity("C")] = Arc()
                conn[Activity("A")] = arcs
            }

            // B -> F
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("F")] = Arc()
                conn[Activity("B")] = arcs
            }

            // C -> F
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("F")] = Arc()
                conn[Activity("C")] = arcs
            }

            // D -> E
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("E")] = Arc()
                conn[Activity("D")] = arcs
            }

            // E -> F
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("F")] = Arc()
                conn[Activity("E")] = arcs
            }
        }

        val assigment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        val expectedLabel = assigment[Activity("A")]!!
        assertEquals(expectedLabel, assigment[Activity("B")])
        assertEquals(expectedLabel, assigment[Activity("C")])
        assertEquals(expectedLabel, assigment[Activity("D")])
        assertEquals(expectedLabel, assigment[Activity("E")])
        assertEquals(expectedLabel, assigment[Activity("F")])
    }

    @Test
    fun `Graph with separated parts`() {
        // Based on Figure 7.21 PM book: L1 = {[a,b,c,d], [a,c,b,d], [a,e,d]} part G1b
        val activities = activitiesSet(
            listOf(
                Activity("B"),
                Activity("C"),
                Activity("E")
            )
        )

        val connections = HashMap<Activity, HashMap<Activity, Arc>>().also { conn ->
            // B -> C
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("C")] = Arc()
                conn[Activity("B")] = arcs
            }

            // C -> B
            HashMap<Activity, Arc>().also { arcs ->
                arcs[Activity("B")] = Arc()
                conn[Activity("C")] = arcs
            }
        }

        val assigment = DirectlyFollowsSubGraph(activities, connections).calculateExclusiveCut()

        // B & C in group
        assertEquals(assigment[Activity("B")], assigment[Activity("C")])

        // E with different label
        assertNotEquals(assigment[Activity("B")], assigment[Activity("E")])
    }
}