package processm.core.models.causalnet

import processm.core.helpers.mapToSet
import processm.core.persistence.connection.DBCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CausalNetVisualizationTest {

    val dbName = "2c68ff5a-6e74-4630-93f1-2b0bda3b5cd1"

    //constructing model represented at Fig 3.12 in "Process Mining" by Wil van der Aalst
    @Test
    fun `insert networks for visualizations`() {
        generateExampleNet()
        generateFlowerNet()
        generateSeparateSequencesNet()
    }

    private fun generateExampleNet() {
        val start = Node("s*")
        val invite = Node("invite")
        val review1 = Node("review1")
        val review2 = Node("review2")
        val timeout1 = Node("timeout1")
        val timeout2 = Node("timeout2")
        val collect = Node("collect")
        val decide = Node("decide")
        val accept = Node("accept")
        val reject = Node("reject")
        val end = Node("e*")

        var mm = MutableCausalNet(start = start, end = end)
        mm.addInstance(start, invite, review1, review2, timeout1, timeout2, collect, decide, accept, reject, end)
        listOf(
            start to invite,
            invite to review1, invite to review2, invite to timeout1, invite to timeout2,
            review1 to collect, review2 to collect, timeout1 to collect, timeout2 to collect,
            collect to decide,
            decide to accept, decide to reject,
            accept to end, reject to end
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(start to invite),
            setOf(invite to review1, invite to timeout2),
            setOf(invite to review1, invite to review2),
            setOf(invite to review2, invite to timeout1),
            setOf(invite to timeout1, invite to timeout2),
            setOf(review1 to collect),
            setOf(review2 to collect),
            setOf(timeout1 to collect),
            setOf(timeout2 to collect),
            setOf(collect to decide),
            setOf(decide to accept),
            setOf(decide to reject),
            setOf(accept to end),
            setOf(reject to end)
        ).map { split -> split.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(start to invite),
            setOf(invite to review1),
            setOf(invite to review2),
            setOf(invite to timeout1),
            setOf(invite to timeout2),
            setOf(review1 to collect, timeout2 to collect),
            setOf(review1 to collect, review2 to collect),
            setOf(review2 to collect, timeout1 to collect),
            setOf(timeout1 to collect, timeout2 to collect),
            setOf(collect to decide),
            setOf(decide to accept),
            setOf(decide to reject),
            setOf(accept to end),
            setOf(reject to end)
        ).map { join -> join.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addJoin(Join(it)) }
        DBSerializer.insert(DBCache.get(dbName), mm)
    }

    private fun generateFlowerNet() {
        val start = Node("s*")
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val end = Node("e*")

        var mm = MutableCausalNet(start = start, end = end)
        mm.addInstance(start, a, b, c, d, e, f, g, end)
        listOf(
            start to a,
            a to b, b to a,
            a to c, c to a,
            a to d, d to a,
            a to e, e to a,
            a to f, f to a,
            a to g, g to a,
            a to end
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(start to a),
            setOf(a to b),
            setOf(b to a),
            setOf(a to c),
            setOf(c to a),
            setOf(a to d),
            setOf(d to a),
            setOf(a to e),
            setOf(e to a),
            setOf(a to f),
            setOf(f to a),
            setOf(a to g),
            setOf(g to a),
            setOf(a to end)
        ).map { split -> split.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(start to a),
            setOf(a to b),
            setOf(b to a),
            setOf(a to c),
            setOf(c to a),
            setOf(a to d),
            setOf(d to a),
            setOf(a to e),
            setOf(e to a),
            setOf(a to f),
            setOf(f to a),
            setOf(a to g),
            setOf(g to a),
            setOf(a to end)
        ).map { join -> join.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addJoin(Join(it)) }
        DBSerializer.insert(DBCache.get(dbName), mm)
    }

    private fun generateSeparateSequencesNet() {
        val start = Node("s*")
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val end = Node("e*")

        var mm = MutableCausalNet(start = start, end = end)
        mm.addInstance(start, a, b, c, d, end)
        listOf(
            start to a, a to b, b to c, c to d, d to end
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(start to a),
            setOf(a to b),
            setOf(b to c),
            setOf(c to d),
            setOf(d to end)
        ).map { split -> split.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(start to a),
            setOf(a to b),
            setOf(b to c),
            setOf(c to d),
            setOf(d to end)
        ).map { join -> join.mapToSet { Dependency(it.first, it.second) } }
            .forEach { mm.addJoin(Join(it)) }
        DBSerializer.insert(DBCache.get(dbName), mm)
    }
}