package processm.directlyfollowsgraph

import processm.core.log.DatabaseXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.ProcessTreeActivity
import processm.core.persistence.DBConnectionPool
import processm.miners.processtree.directlyfollowsgraph.DirectlyFollowsGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectlyFollowsGraphTest {
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

        assertTrue(miner.graph.isEmpty())
        assertTrue(miner.startActivities.isEmpty())
        assertTrue(miner.endActivities.isEmpty())
    }

    @Test
    fun `Build directly follows graph based on log from Definition 6,3 PM book`() {
        val a = ProcessTreeActivity("A")
        val b = ProcessTreeActivity("B")
        val c = ProcessTreeActivity("C")
        val d = ProcessTreeActivity("D")
        val e = ProcessTreeActivity("E")

        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        miner.graph.also { graph ->
            assertEquals(graph.size, 4)

            assertTrue(graph.contains(a))
            assertTrue(graph.contains(b))
            assertTrue(graph.contains(c))
            assertTrue(graph.contains(e))

            assertFalse(graph.contains(d))

            with(graph[a]!!) {
                assertEquals(size, 3)

                assertTrue(contains(b))
                assertEquals(this[b]!!.cardinality, 1)

                assertTrue(contains(c))
                assertEquals(this[c]!!.cardinality, 2)

                assertTrue(contains(e))
                assertEquals(this[e]!!.cardinality, 1)
            }

            with(graph[b]!!) {
                assertEquals(size, 2)

                assertTrue(contains(c))
                assertEquals(this[c]!!.cardinality, 1)

                assertTrue(contains(d))
                assertEquals(this[d]!!.cardinality, 2)
            }

            with(graph[c]!!) {
                assertEquals(size, 2)

                assertTrue(contains(b))
                assertEquals(this[b]!!.cardinality, 2)

                assertTrue(contains(d))
                assertEquals(this[d]!!.cardinality, 1)
            }

            with(graph[e]!!) {
                assertEquals(size, 1)

                assertTrue(contains(d))
                assertEquals(this[d]!!.cardinality, 1)
            }
        }
    }

    @Test
    fun `Graph contains only activities from log, no special added`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertEquals(miner.graph.size, 4)
    }

    @Test
    fun `Start activities stored in special map`() {
        val a = ProcessTreeActivity("A")
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertEquals(miner.startActivities.size, 1)

        assertTrue(miner.startActivities.contains(a))
        assertEquals(miner.startActivities[a]!!.cardinality, 4)
    }

    @Test
    fun `Last activities stored in special map`() {
        val d = ProcessTreeActivity("D")
        val miner = DirectlyFollowsGraph()
        miner.discover(DatabaseHierarchicalXESInputStream(logId))

        assertEquals(miner.endActivities.size, 1)

        assertTrue(miner.endActivities.contains(d))
        assertEquals(miner.endActivities[d]!!.cardinality, 4)
    }
}