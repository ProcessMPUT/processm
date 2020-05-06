package processm.directlyfollowsgraph

import processm.core.log.DBXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
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
        DBXESOutputStream().use { db ->
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
        val miner = DirectlyFollowsGraph()
        miner.discover(DBHierarchicalXESInputStream(logId))

        miner.graph.also { graph ->
            assertEquals(graph.size, 4)

            assertTrue(graph.contains(A))
            assertTrue(graph.contains(B))
            assertTrue(graph.contains(C))
            assertTrue(graph.contains(E))

            assertFalse(graph.contains(D))

            with(graph[A]!!) {
                assertEquals(size, 3)

                assertTrue(contains(B))
                assertEquals(this[B]!!.cardinality, 1)

                assertTrue(contains(C))
                assertEquals(this[C]!!.cardinality, 2)

                assertTrue(contains(E))
                assertEquals(this[E]!!.cardinality, 1)
            }

            with(graph[B]!!) {
                assertEquals(size, 2)

                assertTrue(contains(C))
                assertEquals(this[C]!!.cardinality, 1)

                assertTrue(contains(D))
                assertEquals(this[D]!!.cardinality, 2)
            }

            with(graph[C]!!) {
                assertEquals(size, 2)

                assertTrue(contains(B))
                assertEquals(this[B]!!.cardinality, 2)

                assertTrue(contains(D))
                assertEquals(this[D]!!.cardinality, 1)
            }

            with(graph[E]!!) {
                assertEquals(size, 1)

                assertTrue(contains(D))
                assertEquals(this[D]!!.cardinality, 1)
            }
        }
    }

    @Test
    fun `Graph contains only activities from log, no special added`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DBHierarchicalXESInputStream(logId))

        assertEquals(miner.graph.size, 4)
    }

    @Test
    fun `Start activities stored in special map`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DBHierarchicalXESInputStream(logId))

        assertEquals(miner.startActivities.size, 1)

        assertTrue(miner.startActivities.contains(A))
        assertEquals(miner.startActivities[A]!!.cardinality, 4)
    }

    @Test
    fun `Last activities stored in special map`() {
        val miner = DirectlyFollowsGraph()
        miner.discover(DBHierarchicalXESInputStream(logId))

        assertEquals(miner.endActivities.size, 1)

        assertTrue(miner.endActivities.contains(D))
        assertEquals(miner.endActivities[D]!!.cardinality, 4)
    }
}