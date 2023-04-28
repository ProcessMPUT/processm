package processm.miners.processtree.directlyfollowsgraph

import processm.core.log.Helpers.logFromString
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.models.processtree.ProcessTreeActivity
import kotlin.test.*

class DirectlyFollowsGraphTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")

    @Test
    fun `Empty trace in log - no connections added to graph`() {
        val log = logFromString("")
        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log))

        assertTrue(miner.graph.rows.isEmpty())
        assertTrue(miner.graph.columns.isEmpty())
        assertTrue(miner.initialActivities.isEmpty())
        assertTrue(miner.finalActivities.isEmpty())
    }

    @Test
    fun `Build directly follows graph based on log from Definition 6,3 PM book`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A C B D
            A E D
            """.trimIndent()
        )
        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log))

        miner.graph.also { graph ->
            assertEquals(4, graph.rows.size)
            assertTrue(graph.rows.containsAll(setOf(A, B, C, E)))

            assertEquals(1, graph[A, B]!!.cardinality)
            assertEquals(2, graph[A, C]!!.cardinality)
            assertEquals(1, graph[A, E]!!.cardinality)

            assertEquals(1, graph[B, C]!!.cardinality)
            assertEquals(2, graph[B, D]!!.cardinality)

            assertEquals(2, graph[C, B]!!.cardinality)
            assertEquals(1, graph[C, D]!!.cardinality)

            assertEquals(1, graph[E, D]!!.cardinality)
        }
    }

    @Test
    fun `Graph contains only activities from log, no special added`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A C B D
            A E D
            """.trimIndent()
        )
        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log))

        assertTrue(miner.graph.rows.containsAll(setOf(A, B, C, E)))
        assertFalse(miner.graph.rows.contains(D))
    }

    @Test
    fun `Start activities stored in special map`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A C B D
            A E D
            """.trimIndent()
        )
        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log))

        assertEquals(1, miner.initialActivities.size)

        assertTrue(miner.initialActivities.contains(A))
        assertEquals(4, miner.initialActivities[A]!!.cardinality)
    }

    @Test
    fun `Last activities stored in special map`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A C B D
            A E D
            """.trimIndent()
        )
        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log))

        assertEquals(1, miner.finalActivities.size)

        assertTrue(miner.finalActivities.contains(D))
        assertEquals(4, miner.finalActivities[D]!!.cardinality)
    }

    @Test
    fun `Build graph as diff`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        val diff = miner.discoverDiff(sequenceOf(log))

        assertNull(diff)
    }

    @Test
    fun `DFG inside miner changed in discoverDiff action`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()

        assertTrue(miner.graph.rows.isEmpty())
        assertTrue(miner.graph.columns.isEmpty())
        assertTrue(miner.initialActivities.isEmpty())
        assertTrue(miner.finalActivities.isEmpty())

        miner.discoverDiff(sequenceOf(log))

        assertTrue(miner.graph.rows.isNotEmpty())
        assertTrue(miner.graph.columns.isNotEmpty())
        assertTrue(miner.initialActivities.isNotEmpty())
        assertTrue(miner.finalActivities.isNotEmpty())
    }

    @Test
    fun `Update DFG based on discovered diff`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()

        assertTrue(miner.graph.rows.isEmpty())
        assertTrue(miner.graph.columns.isEmpty())
        assertTrue(miner.initialActivities.isEmpty())
        assertTrue(miner.finalActivities.isEmpty())

        miner.discoverDiff(sequenceOf(log))

        assertEquals(1, miner.graph[A, B]!!.cardinality)
        assertEquals(1, miner.graph[A, C]!!.cardinality)
        assertEquals(1, miner.graph[B, C]!!.cardinality)
        assertEquals(1, miner.graph[B, D]!!.cardinality)
        assertEquals(1, miner.graph[C, B]!!.cardinality)
        assertEquals(1, miner.graph[C, D]!!.cardinality)

        assertEquals(1, miner.initialActivities.size)
        assertTrue(miner.initialActivities.contains(A))

        assertEquals(1, miner.finalActivities.size)
        assertTrue(miner.finalActivities.contains(D))
    }

    @Test
    fun `Diff changes list - start activities updated`() {
        val baseLog = logFromString(
            """
            A C D
            """.trimIndent()
        )
        val log = logFromString(
            """
            B C D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        val diff = miner.discoverDiff(sequenceOf(log))

        assertNull(diff)
    }

    @Test
    fun `Diff changes list - end activities updated`() {
        val baseLog = logFromString(
            """
            A C D
            """.trimIndent()
        )
        val log = logFromString(
            """
            A C E
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        val diff = miner.discoverDiff(sequenceOf(log))

        assertNull(diff)
    }

    @Test
    fun `Diff changes list - no new connections - empty collection as response`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A B C D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        val diff = miner.discoverDiff(sequenceOf(log2))

        assertNotNull(diff)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `Diff changes list - new activity found (E)`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A E D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        val diff = miner.discoverDiff(sequenceOf(log2))

        assertNull(diff)
    }

    @Test
    fun `Diff changes list - new connections between activities`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A C C B C D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        val diff = miner.discoverDiff(sequenceOf(log2))

        assertNotNull(diff)
        assertEquals(1, diff.size)
        assertTrue(diff.contains(C to C))
    }

    @Test
    fun `Remove log from DFG`() {
        val baseLog = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val logToRemove = logFromString(
            """
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        val diff = miner.discoverRemovedPartOfGraph(sequenceOf(logToRemove))

        assertEquals(3, diff!!.size)
        assertTrue(diff.contains(A to C))
        assertTrue(diff.contains(C to B))
        assertTrue(diff.contains(B to D))

        assertEquals(setOf(A, B, C), miner.graph.rows)
        assertEquals(setOf(B, C, D), miner.graph.columns)
    }

    @Test
    fun `Remove log from DFG - no removed connections between pair of activities`() {
        val baseLog = logFromString(
            """
            A B C D
            A C B D
            A C B D
            A C B D
            """.trimIndent()
        )
        val logToRemove = logFromString(
            """
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        val diff = miner.discoverRemovedPartOfGraph(sequenceOf(logToRemove))

        assertTrue(diff!!.isEmpty())
    }

    @Test
    fun `Remove log from DFG - removed activity from DFG`() {
        val baseLog = logFromString(
            """
            A B C D
            A C B D
            A E D
            """.trimIndent()
        )
        val logToRemove = logFromString(
            """
            A E D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        assertEquals(setOf(A, B, C, E), miner.graph.rows)
        assertEquals(setOf(B, C, E, D), miner.graph.columns)

        val diff = miner.discoverRemovedPartOfGraph(sequenceOf(logToRemove))

        assertNull(diff)
        assertEquals(setOf(A, B, C), miner.graph.rows)
        assertEquals(setOf(B, C, D), miner.graph.columns)
    }

    @Test
    fun `Remove log from DFG - removed start activity from DFG`() {
        val baseLog = logFromString(
            """
            A B C
            A C
            B C
            """.trimIndent()
        )
        val logToRemove = logFromString(
            """
            B C
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        assertEquals(setOf(A, B), miner.graph.rows)
        assertEquals(setOf(B, C), miner.graph.columns)

        val diff = miner.discoverRemovedPartOfGraph(sequenceOf(logToRemove))

        assertNull(diff)
        assertEquals(setOf(A, B), miner.graph.rows)
        assertEquals(setOf(B, C), miner.graph.columns)
    }

    @Test
    fun `Remove log from DFG - removed end activity from DFG`() {
        val baseLog = logFromString(
            """
            A B C D
            A B C
            """.trimIndent()
        )
        val logToRemove = logFromString(
            """
            A B C
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(baseLog))

        assertEquals(setOf(A, B, C), miner.graph.rows)
        assertEquals(setOf(B, C, D), miner.graph.columns)

        val diff = miner.discoverRemovedPartOfGraph(sequenceOf(logToRemove))

        assertNull(diff)
        assertEquals(setOf(A, B, C), miner.graph.rows)
        assertEquals(setOf(B, C, D), miner.graph.columns)
    }

    @Test
    fun `Update total traces count`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A E D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        assertEquals(2, miner.tracesCount)

        miner.discover(sequenceOf(log2))

        assertEquals(2 + 1, miner.tracesCount)
    }

    @Test
    fun `Activities statistics - duplicated activities`() {
        val log1 = logFromString(
            """
            A B C
            A B B C
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A B B C D D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        assertEquals(1, miner.activitiesDuplicatedInTraces.size)
        assertEquals(1, miner.activitiesDuplicatedInTraces[B])

        miner.discover(sequenceOf(log2))

        assertEquals(2, miner.activitiesDuplicatedInTraces.size)
        assertEquals(2, miner.activitiesDuplicatedInTraces[B])
        assertEquals(1, miner.activitiesDuplicatedInTraces[D])
    }

    @Test
    fun `Activities statistics - traces support`() {
        val log1 = logFromString(
            """
            A B C B D
            A C B D
            """.trimIndent()
        )
        val log2 = logFromString(
            """
            A E D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        miner.discover(sequenceOf(log1))

        assertEquals(2, miner.activityTraceSupport[A])
        assertEquals(2, miner.activityTraceSupport[B])
        assertEquals(2, miner.activityTraceSupport[C])
        assertEquals(2, miner.activityTraceSupport[D])
        assertNull(miner.activityTraceSupport[E])

        miner.discover(sequenceOf(log2))

        assertEquals(3, miner.activityTraceSupport[A])
        assertEquals(2, miner.activityTraceSupport[B])
        assertEquals(2, miner.activityTraceSupport[C])
        assertEquals(3, miner.activityTraceSupport[D])
        assertEquals(1, miner.activityTraceSupport[E])
    }

    @Test
    fun `Maximum support - activities in collection`() {
        val log = logFromString(
            """
            A B C D
            A C B D
            A E D
            """.trimIndent()
        )

        val dfg = DirectlyFollowsGraph()
        dfg.discover(sequenceOf(log))

        assertEquals(3, dfg.maximumTraceSupport(listOf(A, B, C, D, E)))
    }

    @Test
    fun `Maximum support - not found activity, return 0 as default`() {
        val log = logFromString(
            """
            A B C
            A B D
            """.trimIndent()
        )

        val dfg = DirectlyFollowsGraph()
        dfg.discover(sequenceOf(log))

        assertEquals(0, dfg.maximumTraceSupport(listOf(E)))
    }

    @Test
    fun `Maximum support - part of activities in collection not used in analyzed traces`() {
        val log = logFromString(
            """
            A B C
            A B D
            """.trimIndent()
        )

        val dfg = DirectlyFollowsGraph()
        dfg.discover(sequenceOf(log))

        assertEquals(2, dfg.maximumTraceSupport(listOf(A, E)))
    }
}
