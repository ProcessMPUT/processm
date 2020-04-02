package processm.miners.processtree.inductiveminer

import org.junit.jupiter.api.assertThrows
import processm.core.models.processtree.Activity
import processm.core.models.processtree.SilentActivity
import processm.miners.processtree.directlyfollowsgraph.Arc
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `Fetch silent activity from graph - no activities in graph`() {
        val graph = DirectlyFollowsSubGraph(activitiesSet(listOf()), hashMapOf())

        assertEquals(SilentActivity(), graph.finishCalculations())
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
}