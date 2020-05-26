package processm.directlyfollowsgraph

import processm.core.models.processtree.ProcessTreeActivity
import processm.miners.heuristicminer.Helper.logFromString
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
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
        assertTrue(miner.startActivities.isEmpty())
        assertTrue(miner.endActivities.isEmpty())
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

        assertEquals(1, miner.startActivities.size)

        assertTrue(miner.startActivities.contains(A))
        assertEquals(4, miner.startActivities[A]!!.cardinality)
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

        assertEquals(1, miner.endActivities.size)

        assertTrue(miner.endActivities.contains(D))
        assertEquals(4, miner.endActivities[D]!!.cardinality)
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
        assertTrue(miner.startActivities.isEmpty())
        assertTrue(miner.endActivities.isEmpty())

        miner.discoverDiff(sequenceOf(log))

        assertTrue(miner.graph.rows.isNotEmpty())
        assertTrue(miner.graph.columns.isNotEmpty())
        assertTrue(miner.startActivities.isNotEmpty())
        assertTrue(miner.endActivities.isNotEmpty())
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
        assertTrue(miner.startActivities.isEmpty())
        assertTrue(miner.endActivities.isEmpty())

        miner.discoverDiff(sequenceOf(log))

        assertEquals(1, miner.graph[A, B]!!.cardinality)
        assertEquals(1, miner.graph[A, C]!!.cardinality)
        assertEquals(1, miner.graph[B, C]!!.cardinality)
        assertEquals(1, miner.graph[B, D]!!.cardinality)
        assertEquals(1, miner.graph[C, B]!!.cardinality)
        assertEquals(1, miner.graph[C, D]!!.cardinality)

        assertEquals(1, miner.startActivities.size)
        assertTrue(miner.startActivities.contains(A))

        assertEquals(1, miner.endActivities.size)
        assertTrue(miner.endActivities.contains(D))
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
}