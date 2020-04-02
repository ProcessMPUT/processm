package processm.miners.processtree.inductiveminer

import processm.core.models.processtree.Activity
import processm.miners.processtree.directlyfollowsgraph.Arc
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DirectlyFollowsSubGraphTest {
    private fun activitiesSet(l: Collection<Activity>) = HashSet<Activity>().also {
        it.addAll(l)
    }

    @Test
    fun `Possible to finish calculations if not contains activities`() {
        val graph = DirectlyFollowsSubGraph(activitiesSet(emptyList()), hashMapOf())

        assertTrue(graph.canFinishCalculationsOnSubGraph())
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
}