package processm.directlyfollowsgraph

import processm.core.log.DatabaseXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.DatabaseHierarchicalXESInputStream
import processm.core.models.processtree.Activity
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
    fun `Build directly follows graph based on log from Definition 6,3 PM book`() {
        val a = Activity("A")
        val b = Activity("B")
        val c = Activity("C")
        val d = Activity("D")
        val e = Activity("E")
        val source = Activity("SOURCE", isSpecial = true)
        val sink = Activity("SINK", isSpecial = true)

        DirectlyFollowsGraph().mine(DatabaseHierarchicalXESInputStream(logId)).also { graph ->
            assertEquals(graph.size, 6)

            assertTrue(graph.contains(source))

            assertTrue(graph.contains(a))
            assertTrue(graph.contains(b))
            assertTrue(graph.contains(c))
            assertTrue(graph.contains(d))
            assertTrue(graph.contains(e))

            assertFalse(graph.contains(sink))

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

            with(graph[d]!!) {
                assertEquals(size, 1)

                assertTrue(contains(sink))
                assertEquals(this[sink]!!.cardinality, 4)
            }

            with(graph[e]!!) {
                assertEquals(size, 1)

                assertTrue(contains(d))
                assertEquals(this[d]!!.cardinality, 1)
            }
        }
    }

    @Test
    fun `Graph contains extra SOURCE activity with reference to start activities`() {
        val source = Activity("SOURCE", isSpecial = true)
        val a = Activity("A")

        DirectlyFollowsGraph().mine(DatabaseHierarchicalXESInputStream(logId)).also { graph ->
            assertTrue(graph.contains(source))

            with(graph[source]!!) {
                assertEquals(size, 1)

                assertTrue(contains(a))
                with(this[a]!!) {
                    assertEquals(cardinality, 4)
                }
            }
        }
    }
}