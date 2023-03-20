package processm.core.models.petrinet

import processm.core.DBTestHelper
import processm.core.persistence.connection.DBCache
import java.util.*
import kotlin.NoSuchElementException
import kotlin.test.*

class DBSerializerTest {

    val start = Place()
    val c1 = Place()
    val c2 = Place()
    val c3 = Place()
    val c4 = Place()
    val c5 = Place()
    val end = Place()
    val a = Transition("a", listOf(start), listOf(c1, c2))
    val b = Transition("b", listOf(c1), listOf(c3))
    val c = Transition("c", listOf(c1), listOf(c3))
    val d = Transition("d", listOf(c2), listOf(c4))
    val e = Transition("e", listOf(c3, c4), listOf(c5))
    val f = Transition("f", listOf(c5), listOf(c1, c2))
    val g = Transition("g", listOf(c5), listOf(end))
    val h = Transition("h", listOf(c5), listOf(end))
    val originalNet = PetriNet(
        listOf(start, c1, c2, c3, c4, c5, end),
        listOf(a, b, c, d, e, f, g, h),
        Marking(start),
        Marking(end)
    )

    @Test
    fun `insert fetch and compare`() {
        val id = DBSerializer.insert(DBCache.get(DBTestHelper.dbName).database, originalNet)
        val net = DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, id)

        assertEquals(setOf(a, b, c, d, e, f, g, h), net.activities.toSet())
        assertEquals(setOf(start, c1, c2, c3, c4, c5, end), net.places.toSet())
        assertEquals(setOf(a), net.startActivities.toSet())
        assertEquals(setOf(g, h), net.endActivities.toSet())
        val decisionPoints = net.decisionPoints.toList()
        assertEquals(2, decisionPoints.size)
        assertTrue(DecisionPoint(setOf(c1), setOf(b, c), setOf(a, f)) in decisionPoints)
        assertTrue(DecisionPoint(setOf(c5), setOf(f, g, h), setOf(e)) in decisionPoints)
    }

    @Test
    fun `insert fetch delete fetch`() {
        val id = DBSerializer.insert(DBCache.get(DBTestHelper.dbName).database, originalNet)
        DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, id)
        DBSerializer.delete(DBCache.get(DBTestHelper.dbName).database, id)
        assertFailsWith<NoSuchElementException> { DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, id) }
    }

    @Test
    fun `fetch nonexisting model`() {
        assertFailsWith<NoSuchElementException> {
            DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, UUID.randomUUID())
        }
    }

    @Test
    fun `delete nonexisting model`() {
        assertFailsWith<NoSuchElementException> {
            DBSerializer.delete(DBCache.get(DBTestHelper.dbName).database, UUID.randomUUID())
        }
    }
}