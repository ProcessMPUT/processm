package processm.core.models.causalnet

import java.lang.IllegalArgumentException
import kotlin.test.*

class MutableCausalNetTest {

    //activities inspired by Fig 3.12 in "Process Mining" by Van van der Alst
    private val a = Node("register request")
    private val b = Node("examine thoroughly")
    private val c = Node("examine casually")
    private val d = Node("check ticket")
    private val e = Node("decide")
    private val f = Node("reinitiate request")
    private val g = Node("pay compensation")
    private val h = Node("reject request")
    private val z = Node("end")

    //constructing model represented at Fig 3.12 in "Process Mining" by Wil van der Aalst
    @Test
    fun constructModel() {
        var mm = MutableCausalNet(start = a, end = z)
        mm.addInstance(a, b, c, d, e, f, g, h, z)
        listOf(
            a to b, a to c, a to d, b to e, c to e, d to e, e to f, e to g,
            e to h, f to d, f to c, f to b, g to z, h to z
        ).forEach { mm.addDependency(it.first, it.second) }
        listOf(
            setOf(a to b, a to d),
            setOf(a to c, a to d),
            setOf(b to e),
            setOf(c to e),
            setOf(d to e),
            setOf(e to g),
            setOf(e to h),
            setOf(e to f),
            setOf(f to d, f to b),
            setOf(f to d, f to c),
            setOf(g to z),
            setOf(h to z)
        ).map { split -> split.map { Dependency(it.first, it.second) }.toSet() }
            .forEach { mm.addSplit(Split(it)) }
        listOf(
            setOf(a to b),
            setOf(f to b),
            setOf(a to c),
            setOf(f to c),
            setOf(a to d),
            setOf(f to d),
            setOf(b to e, d to e),
            setOf(c to e, d to e),
            setOf(e to f),
            setOf(e to g),
            setOf(e to h),
            setOf(g to z),
            setOf(h to z)
        ).map { join -> join.map { Dependency(it.first, it.second) }.toSet() }
            .forEach { mm.addJoin(Join(it)) }
        assertEquals(setOf(a, b, c, d, e, f, g, h, z), mm.instances)
        assertTrue(mm.outgoing[a]?.all { it.source == a } == true)
        assertEquals(setOf(b, c, d), mm.outgoing[a]?.map { it.target }?.toSet())
        assertFalse { z in mm.outgoing }
        assertTrue { mm.incoming[z]?.all { it.target == z } == true }
        assertEquals(setOf(g, h), mm.incoming[z]?.map { it.source }?.toSet())
        assertEquals(2, mm.splits[a]?.size)
        assertEquals(1, mm.splits[b]?.size)
        assertFalse { z in mm.splits }
        assertEquals(2, mm.joins[e]?.size)
        assertEquals(1, mm.joins[f]?.size)
        assertEquals(setOf(Dependency(e, f)), mm.joins[f]?.first()?.dependencies)
        assertFalse { a in mm.joins }
    }

    @Test
    fun addDependencyWithUnknownTarget() {
        assertFailsWith(IllegalArgumentException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(a)
            mm.addDependency(a, b)
        }
    }

    @Test
    fun addDependencyWithUnknownSource() {
        assertFailsWith(IllegalArgumentException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(b)
            mm.addDependency(Dependency(a, b))
        }
    }

    @Test
    fun addSplitWithNoInstance() {
        assertFailsWith(NoSuchElementException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(a)
            mm.addSplit(Split(setOf(Dependency(a, b))))
        }
    }

    @Test
    fun addSplitWithNoDependency() {
        assertFailsWith(NoSuchElementException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(a, b)
            mm.addSplit(Split(setOf(Dependency(a, b))))
        }
    }


    @Test
    fun addJoinWithNoInstance() {
        assertFailsWith(NoSuchElementException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(a)
            mm.addJoin(Join(setOf(Dependency(a, b))))
        }
    }

    @Test
    fun addJoinWithNoDependency() {
        assertFailsWith(NoSuchElementException::class) {
            val mm = MutableCausalNet()
            mm.addInstance(a, b)
            mm.addJoin(Join(setOf(Dependency(a, b))))
        }
    }

    @Test
    fun defaultStartAndEnd() {
        val mm = MutableCausalNet()
        assertTrue { mm.start in mm.instances }
        assertTrue { mm.end in mm.instances }
        assertTrue { mm.start.special }
        assertTrue { mm.end.special }
    }

    @Test
    fun multipleActivityInstancesWithSelfLoop() {
        val mm = MutableCausalNet()
        val a = "a"
        val a1 = Node(a, "1")
        val a2 = Node(a, "2")
        mm.addInstance(Node("a", "1"), a2)
        assertEquals(setOf(a1, a2, mm.start, mm.end), mm.instances)
        mm.addDependency(mm.start, a1)
        assertTrue { mm.incoming[mm.start].isNullOrEmpty() }
        assertEquals(setOf(Dependency(mm.start, a1)), mm.outgoing[mm.start])
        assertEquals(setOf(Dependency(mm.start, a1)), mm.incoming[a1])
        mm.addDependency(a1, a2)
        assertEquals(setOf(Dependency(a1, a2)), mm.outgoing[a1])
        assertEquals(setOf(Dependency(a1, a2)), mm.incoming[a2])
        mm.addDependency(a1, mm.end)
        assertEquals(setOf(Dependency(a1, a2), Dependency(a1, mm.end)), mm.outgoing[a1])
        assertEquals(setOf(Dependency(a1, mm.end)), mm.incoming[mm.end])
        mm.addDependency(a2, a2)
        assertEquals(setOf(Dependency(a1, a2), Dependency(a2, a2)), mm.incoming[a2])
        assertEquals(setOf(Dependency(a2, a2)), mm.outgoing[a2])
        mm.addDependency(a2, mm.end)
        assertEquals(setOf(Dependency(a1, mm.end), Dependency(a2, mm.end)), mm.incoming[mm.end])
        assertEquals(setOf(Dependency(a2, a2), Dependency(a2, mm.end)), mm.outgoing[a2])
        assertTrue { mm.outgoing[mm.end].isNullOrEmpty() }
    }

    @Test
    fun singleActivityInstanceGraph() {
        val a = Node("a")
        val mm = MutableCausalNet(start = a, end = a)
        mm.addInstance(a)
        assertEquals(setOf(a), mm.instances)
        assertTrue { mm.outgoing[a].isNullOrEmpty() }
        assertTrue { mm.incoming[a].isNullOrEmpty() }
        mm.addDependency(a, a)
        assertEquals(setOf(Dependency(a, a)), mm.outgoing[a])
        assertEquals(setOf(Dependency(a, a)), mm.incoming[a])
        mm.addSplit(Split(setOf(Dependency(a, a))))
        mm.addJoin(Join(setOf(Dependency(a, a))))
        assertEquals(setOf(Split(setOf(Dependency(a, a)))), mm.splits[a])
        assertEquals(setOf(Join(setOf(Dependency(a, a)))), mm.joins[a])
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun removeSplit() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        val d = mm.addDependency(mm.start, a)
        val s = Split(setOf(d))
        mm.addSplit(s)
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.splits as MutableMap).remove(s.source)
        }
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.splits.getValue(s.source) as MutableSet).remove(s)
        }
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun removeJoin() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        val d = mm.addDependency(mm.start, a)
        val s = Join(setOf(d))
        mm.addJoin(s)
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.joins as MutableMap).remove(s.target)
        }
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.joins.getValue(s.target) as MutableSet).remove(s)
        }
    }

    @Test
    fun removeExistingJoin() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        mm.addInstance(b)
        val d1 = mm.addDependency(mm.start, a)
        val d2 = mm.addDependency(b, a)
        assertFalse { mm.contains(Join(setOf(d2))) }
        mm.addJoin(Join(setOf(d1)))
        mm.addJoin(Join(setOf(d2)))
        assertTrue { mm.contains(Join(setOf(d2))) }
        assertEquals(setOf(Join(setOf(d1)), Join(setOf(d2))), mm.joins[a])
        mm.removeJoin(Join(setOf(d2)))
        assertEquals(setOf(Join(setOf(d1))), mm.joins[a])
        assertFalse { mm.contains(Join(setOf(d2))) }
    }

    @Test
    fun removeNonexistingJoin() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        mm.addInstance(b)
        val d1 = mm.addDependency(mm.start, a)
        val d2 = mm.addDependency(b, a)
        mm.addJoin(Join(setOf(d1)))
        assertEquals(setOf(Join(setOf(d1))), mm.joins[a])
        mm.removeJoin(Join(setOf(d2)))
        assertEquals(setOf(Join(setOf(d1))), mm.joins[a])
    }

    @Test
    fun removeExistingSplit() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        mm.addInstance(b)
        val d1 = mm.addDependency(mm.start, a)
        val d2 = mm.addDependency(mm.start, b)
        assertFalse { mm.contains(Split(setOf(d2))) }
        mm.addSplit(Split(setOf(d1)))
        mm.addSplit(Split(setOf(d2)))
        assertTrue { mm.contains(Split(setOf(d2))) }
        assertEquals(setOf(Split(setOf(d1)), Split(setOf(d2))), mm.splits[mm.start])
        mm.removeSplit(Split(setOf(d2)))
        assertEquals(setOf(Split(setOf(d1))), mm.splits[mm.start])
        assertFalse { mm.contains(Split(setOf(d2))) }
    }

    @Test
    fun removeNonexistingSplit() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        mm.addInstance(b)
        val d1 = mm.addDependency(mm.start, a)
        val d2 = mm.addDependency(mm.start, b)
        mm.addSplit(Split(setOf(d1)))
        assertEquals(setOf(Split(setOf(d1))), mm.splits[mm.start])
        mm.removeSplit(Split(setOf(d2)))
        assertEquals(setOf(Split(setOf(d1))), mm.splits[mm.start])
    }

    @Ignore("We decided that protecting against it is too expensive")
    @Test
    fun removeDependency() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        val d = mm.addDependency(mm.start, a)
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.outgoing as MutableMap).remove(d.source)
        }
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.outgoing.getValue(d.source) as MutableSet).remove(d)
        }
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.incoming as MutableMap).remove(d.target)
        }
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.incoming.getValue(d.target) as MutableSet).remove(d)
        }
    }

    @Test
    fun removeActivityInstance() {
        val a = Node("a")
        val mm = MutableCausalNet()
        mm.addInstance(a)
        assertFailsWith(UnsupportedOperationException::class) {
            (mm.instances as MutableSet).remove(a)
        }
    }

    @Test
    fun addSameSplitTwice() {
        val mm = MutableCausalNet()
        val d = mm.addDependency(mm.start, mm.end)
        mm.addSplit(Split(setOf(d)))
        assertFailsWith(IllegalArgumentException::class) {
            mm.addSplit(Split(setOf(d)))
        }
    }

    @Test
    fun addSameJoinTwice() {
        val mm = MutableCausalNet()
        val d = mm.addDependency(mm.start, mm.end)
        mm.addJoin(Join(setOf(d)))
        assertFailsWith(IllegalArgumentException::class) {
            mm.addJoin(Join(setOf(d)))
        }
    }

    @Test
    fun `common interface`() {
        val mm = MutableCausalNet()
        assertEquals(setOf(mm.start), mm.startActivities.toSet())
        assertEquals(setOf(mm.end), mm.endActivities.toSet())
        assertEquals(mm.instances, mm.activities.toSet())
    }

    @Test
    fun `remove all joins`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val mm = MutableCausalNet()
        mm.addInstance(a, b, c)
        mm.addJoin(Join(setOf(mm.addDependency(a, c))))
        mm.addJoin(Join(setOf(mm.addDependency(b, c))))
        mm.addJoin(Join(setOf(mm.addDependency(a, a))))
        mm.addJoin(Join(setOf(mm.addDependency(b, a))))
        mm.addJoin(Join(setOf(mm.addDependency(c, a))))
        assertEquals(2, mm.joins[c]?.size)
        assertEquals(3, mm.joins[a]?.size)
        mm.clearSplits()
        assertEquals(2, mm.joins[c]?.size)
        assertEquals(3, mm.joins[a]?.size)
        mm.clearJoins()
        assertTrue { mm.joins.isEmpty() }
        assertTrue { mm.joins[c].isNullOrEmpty() }
        assertTrue { mm.joins[a].isNullOrEmpty() }
    }

    @Test
    fun `remove all splits`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val mm = MutableCausalNet()
        mm.addInstance(a, b, c)
        mm.addSplit(Split(setOf(mm.addDependency(c, a))))
        mm.addSplit(Split(setOf(mm.addDependency(c, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, a))))
        mm.addSplit(Split(setOf(mm.addDependency(a, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, c))))
        assertEquals(2, mm.splits[c]?.size)
        assertEquals(3, mm.splits[a]?.size)
        mm.clearJoins()
        assertEquals(2, mm.splits[c]?.size)
        assertEquals(3, mm.splits[a]?.size)
        mm.clearSplits()
        assertTrue { mm.splits.isEmpty() }
        assertTrue { mm.splits[c].isNullOrEmpty() }
        assertTrue { mm.splits[a].isNullOrEmpty() }
    }

    @Test
    fun `remove all bindings`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val mm = MutableCausalNet()
        mm.addInstance(a, b, c)
        mm.addJoin(Join(setOf(mm.addDependency(a, c))))
        mm.addJoin(Join(setOf(mm.addDependency(b, c))))
        mm.addJoin(Join(setOf(mm.addDependency(a, a))))
        mm.addJoin(Join(setOf(mm.addDependency(b, a))))
        mm.addJoin(Join(setOf(mm.addDependency(c, a))))
        mm.addSplit(Split(setOf(mm.addDependency(c, a))))
        mm.addSplit(Split(setOf(mm.addDependency(c, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, a))))
        mm.addSplit(Split(setOf(mm.addDependency(a, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, c))))
        assertEquals(2, mm.joins[c]?.size)
        assertEquals(3, mm.joins[a]?.size)
        assertEquals(2, mm.splits[c]?.size)
        assertEquals(3, mm.splits[a]?.size)
        mm.clearBindings()
        assertTrue { mm.joins.isEmpty() }
        assertTrue { mm.joins[c].isNullOrEmpty() }
        assertTrue { mm.joins[a].isNullOrEmpty() }
        assertTrue { mm.splits.isEmpty() }
        assertTrue { mm.splits[c].isNullOrEmpty() }
        assertTrue { mm.splits[a].isNullOrEmpty() }
    }

    @Test
    fun `remove all bindings for given node`() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val mm = MutableCausalNet()
        mm.addInstance(a, b, c)
        mm.addJoin(Join(setOf(mm.addDependency(a, c))))
        mm.addJoin(Join(setOf(mm.addDependency(b, c))))
        mm.addJoin(Join(setOf(mm.addDependency(a, a))))
        mm.addJoin(Join(setOf(mm.addDependency(b, a))))
        mm.addJoin(Join(setOf(mm.addDependency(c, a))))
        mm.addSplit(Split(setOf(mm.addDependency(c, a))))
        mm.addSplit(Split(setOf(mm.addDependency(c, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, a))))
        mm.addSplit(Split(setOf(mm.addDependency(a, b))))
        mm.addSplit(Split(setOf(mm.addDependency(a, c))))
        assertEquals(2, mm.joins[c]?.size)
        assertEquals(3, mm.joins[a]?.size)
        assertEquals(2, mm.splits[c]?.size)
        assertEquals(3, mm.splits[a]?.size)
        mm.clearBindingsFor(c)
        assertFalse { mm.joins.isEmpty() }
        assertTrue { mm.joins[c].isNullOrEmpty() }
        assertFalse { mm.joins[a].isNullOrEmpty() }
        assertFalse { mm.splits.isEmpty() }
        assertTrue { mm.splits[c].isNullOrEmpty() }
        assertFalse { mm.splits[a].isNullOrEmpty() }
    }
}