package processm.etl.metamodel

import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.*

class DAGBusinessPerspectiveExplorerTest {

    companion object {

        /**
         * This is ugly, ill-conceived and in general intended only for use with a fixed argument in test code
         *
         * @param text A value returned by `toString` called on a [org.jgrapht.Graph]
         */
        fun graphFromString(text: String): DefaultDirectedGraph<EntityID<Int>, String> {
            val reText = Regex("^\\(\\[(.*)\\], \\[(.*)\\]\\)$")
            val reEdge = Regex("(\\S+)=\\((\\d+),(\\d+)\\)")

            val m = checkNotNull(reText.matchEntire(text))
            val vertices = m.groupValues[1]
            val edges = m.groupValues[2]

            val table = mockk<IdTable<Int>>()
            val graph = DefaultDirectedGraph<EntityID<Int>, String>(String::class.java)

            for (v in vertices.split(", "))
                graph.addVertex(EntityID(v.toInt(), table))

            for (m in reEdge.findAll(edges)) {
                val label = m.groupValues[1]
                val source = m.groupValues[2].toInt()
                val target = m.groupValues[3].toInt()
                println("$label | $source | $target")
                graph.addEdge(EntityID(source, table), EntityID(target, table), label)
            }

            assertEquals(text, graph.toString())
            return graph
        }

        //This graph is the result of calling getRelationshipGraph() on the Postgres version of the Sakila DB
        val sakilaGraphSerialization =
            """([18, 10, 6, 12, 7, 4, 8, 1, 9, 21, 3, 15, 17, 2, 11, 5, 20, 19, 16, 14, 13], [payment_rental_id_fkey=(18,10), payment_p2007_03_customer_id_fkey=(6,12), staff_address_id_fkey=(7,4), payment_p2007_03_rental_id_fkey=(6,10), payment_p2007_06_staff_id_fkey=(8,7), payment_p2007_03_staff_id_fkey=(6,7), payment_p2007_02_rental_id_fkey=(1,10), payment_p2007_05_customer_id_fkey=(9,12), staff_store_id_fkey=(7,21), payment_p2007_04_customer_id_fkey=(3,12), rental_staff_id_fkey=(10,7), film_language_id_fkey=(15,17), payment_p2007_01_customer_id_fkey=(2,12), city_country_id_fkey=(11,5), payment_p2007_01_staff_id_fkey=(2,7), customer_address_id_fkey=(12,4), film_category_category_id_fkey=(20,19), payment_p2007_02_staff_id_fkey=(1,7), rental_customer_id_fkey=(10,12), payment_p2007_02_customer_id_fkey=(1,12), film_category_film_id_fkey=(20,15), payment_p2007_01_rental_id_fkey=(2,10), rental_inventory_id_fkey=(10,16), inventory_store_id_fkey=(16,21), film_actor_film_id_fkey=(14,15), store_address_id_fkey=(21,4), payment_p2007_06_rental_id_fkey=(8,10), payment_p2007_05_staff_id_fkey=(9,7), payment_p2007_05_rental_id_fkey=(9,10), store_manager_staff_id_fkey=(21,7), payment_p2007_06_customer_id_fkey=(8,12), address_city_id_fkey=(4,11), payment_p2007_04_rental_id_fkey=(3,10), payment_staff_id_fkey=(18,7), customer_store_id_fkey=(12,21), payment_p2007_04_staff_id_fkey=(3,7), film_actor_actor_id_fkey=(14,13), inventory_film_id_fkey=(16,15), payment_customer_id_fkey=(18,12)])"""

    }

    @Test
    fun `calculateVertexWeights on the original Sakila graph throws`() {
        val graph = graphFromString(sakilaGraphSerialization)
        assertThrows<IllegalStateException> { graph.calculateVertexWeights() }
    }

    @Test
    fun `calculateVertexWeights on the acyclic Sakila graph succeeds`() {
        val graph = graphFromString(sakilaGraphSerialization)
        graph.breakCycles()
        val weights = graph.calculateVertexWeights()
        assertEquals(graph.vertexSet(), weights.keys)
    }

    @Test
    fun `calculateVertexWeights for a simple DAG`() {
        val graph = DefaultDirectedGraph<Int, String>(String::class.java)
        for (i in 1..8)
            graph.addVertex(i)
        for ((s, t) in listOf(1 to 3, 1 to 4, 2 to 4, 2 to 5, 3 to 6, 4 to 8, 5 to 6, 6 to 7, 6 to 8))
            graph.addEdge(s, t, "$s->$t")
        val weights = graph.calculateVertexWeights()
        assertEquals((1..8).toSet(), weights.keys)
        assertEquals(4.0, weights[1])
        assertEquals(4.0, weights[2])
        assertEquals(3.0, weights[3])
        assertEquals(2.0, weights[4])
        assertEquals(3.0, weights[5])
        assertEquals(2.0, weights[6])
        assertEquals(1.0, weights[7])
        assertEquals(1.0, weights[8])
    }

    @Test
    fun `breaking cycles yields an acyclic graph`() {
        val graph = DefaultDirectedGraph<Int, String>(String::class.java)
        for (i in 4 downTo 1)
            graph.addVertex(i)
        for ((s, t) in listOf(1 to 2, 2 to 3, 3 to 1, 3 to 4, 4 to 2))
            graph.addEdge(s, t, "$s->$t")
        assertFalse { SzwarcfiterLauerSimpleCycles(graph).findSimpleCycles().isEmpty() }
        graph.breakCycles()
        assertTrue { SzwarcfiterLauerSimpleCycles(graph).findSimpleCycles().isEmpty() }
    }
}