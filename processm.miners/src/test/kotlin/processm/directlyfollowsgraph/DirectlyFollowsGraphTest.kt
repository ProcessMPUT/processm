package processm.directlyfollowsgraph

import processm.core.log.DatabaseXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.persistence.DBConnectionPool
import processm.miners.heuristicminer.Helper.logFromString
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectlyFollowsGraphTest {
    private val A = ProcessTreeActivity("A")
    private val B = ProcessTreeActivity("B")
    private val C = ProcessTreeActivity("C")
    private val D = ProcessTreeActivity("D")
    private val E = ProcessTreeActivity("E")
    private val content = """<?xml version="1.0" encoding="UTF-8" ?>
        <log>
            <trace>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
                <event><string key="concept:name" value="D"/></event>
            </trace>
            <trace>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="D"/></event>
            </trace>
            <trace>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="D"/></event>
            </trace>
            <trace>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="E"/></event>
                <event><string key="concept:name" value="D"/></event>
            </trace>
        </log>
    """.trimIndent()
    private val logId: Int by lazyOf(setUp())
    private fun setUp(): Int {
        DatabaseXESOutputStream().use { db ->
            content.byteInputStream().use { stream ->
                db.write(XMLXESInputStream(stream))
            }
        }

        DBConnectionPool.getConnection().use {
            val response = it.prepareStatement("""SELECT id FROM logs ORDER BY id DESC LIMIT 1""").executeQuery()
            response.next()

            return response.getInt("id")
        }
    }

    @Test
    fun `Empty trace in log - no connections added to graph`() {
        val log: Sequence<Log> = sequenceOf(Log(sequenceOf(Trace())))

        val miner = DirectlyFollowsGraph()
        miner.discover(log)

        assertTrue(miner.graph.rows.isEmpty())
        assertTrue(miner.graph.columns.isEmpty())
        assertTrue(miner.startActivities.isEmpty())
        assertTrue(miner.endActivities.isEmpty())
    }

    @Test
    fun `Build directly follows graph based on log from Definition 6,3 PM book`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        miner.graph.also { graph ->
            assertEquals(4, graph.rows.size)
            assertTrue(graph.rows.containsAll(setOf(A, B, C, E)))

            assertEquals(1, graph[A, B]?.cardinality)
            assertEquals(2, graph[A, C]?.cardinality)
            assertEquals(1, graph[A, E]?.cardinality)

            assertEquals(1, graph[B, C]?.cardinality)
            assertEquals(2, graph[B, D]?.cardinality)

            assertEquals(2, graph[C, B]?.cardinality)
            assertEquals(1, graph[C, D]?.cardinality)

            assertEquals(1, graph[E, D]?.cardinality)
        }
    }

    @Test
    fun `Graph contains only activities from log, no special added`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertTrue(miner.graph.rows.containsAll(setOf(A, B, C, E)))
        assertFalse(miner.graph.rows.contains(D))
    }

    @Test
    fun `Start activities stored in special map`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertEquals(1, miner.startActivities.size)

        assertTrue(miner.startActivities.contains(A))
        assertEquals(4, miner.startActivities[A]!!.cardinality)
    }

    @Test
    fun `Last activities stored in special map`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertEquals(1, miner.endActivities.size)

        assertTrue(miner.endActivities.contains(D))
        assertEquals(4, miner.endActivities[D]!!.cardinality)
    }

    @Test
    fun `Build graph as diff`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()
        val diff = miner.discoverDiff(sequenceOf(log1))

        assertEquals(1, diff.first[A, B]!!.cardinality)
        assertEquals(1, diff.first[A, C]!!.cardinality)
        assertEquals(1, diff.first[B, C]!!.cardinality)
        assertEquals(1, diff.first[B, D]!!.cardinality)
        assertEquals(1, diff.first[C, B]!!.cardinality)
        assertEquals(1, diff.first[C, D]!!.cardinality)

        assertEquals(1, diff.second.size)
        assertTrue(diff.second.contains(A))

        assertEquals(1, diff.third.size)
        assertTrue(diff.third.contains(D))
    }

    @Test
    fun `DFG inside miner not changed in discoverDiff action`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()

        assertEquals(0, miner.graph.rows.size)
        assertEquals(0, miner.graph.columns.size)
        assertEquals(0, miner.startActivities.size)
        assertEquals(0, miner.endActivities.size)

        miner.discoverDiff(sequenceOf(log1))

        assertEquals(0, miner.graph.rows.size)
        assertEquals(0, miner.graph.columns.size)
        assertEquals(0, miner.startActivities.size)
        assertEquals(0, miner.endActivities.size)
    }

    @Test
    fun `Update DFG based on discovered diff`() {
        val log1 = logFromString(
            """
            A B C D
            A C B D
            """.trimIndent()
        )

        val miner = DirectlyFollowsGraph()

        assertEquals(0, miner.graph.rows.size)
        assertEquals(0, miner.graph.columns.size)
        assertEquals(0, miner.startActivities.size)
        assertEquals(0, miner.endActivities.size)

        val diff = miner.discoverDiff(sequenceOf(log1))
        miner.applyDiff(diff)

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
}